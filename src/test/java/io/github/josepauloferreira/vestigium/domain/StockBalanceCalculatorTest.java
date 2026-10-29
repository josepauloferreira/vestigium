package io.github.josepauloferreira.vestigium.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class StockBalanceCalculatorTest {
  private static final ItemId OIL_ID = new ItemId(new UUID(0, 1));
  private static final ItemId BOOTS_ID = new ItemId(new UUID(0, 2));
  private static final Instant OCCURRED_AT = Instant.parse("2026-10-29T13:15:00Z");
  private final StockBalanceCalculator calculator = new StockBalanceCalculator();

  @Test
  void returnsZeroForAnEmptyHistory() {
    assertThat(calculator.calculate(OIL_ID, List.of())).isZero();
  }

  @Test
  void returnsZeroWhenNoEntryContainsTheRequestedItem() {
    assertThat(calculator.calculate(OIL_ID, List.of(entry(101, line(BOOTS_ID, "4"))))).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "0.75", "1.500", "0.0001", "1000000000000000000000000.000001"})
  void derivesASingleEntryMagnitudeWithoutRounding(String input) {
    StockEntry entry = entry(101, line(OIL_ID, input));

    assertThat(calculator.calculate(OIL_ID, List.of(entry))).isEqualByComparingTo(input);
  }

  @Test
  void isolatesEachItemsTotalAcrossEntries() {
    List<StockEntry> entries =
        List.of(
            entry(101, line(OIL_ID, "1.50"), line(BOOTS_ID, "4")),
            entry(102, line(BOOTS_ID, "2"), line(OIL_ID, "0.75")));

    assertThat(calculator.calculate(OIL_ID, entries)).isEqualByComparingTo("2.25");
    assertThat(calculator.calculate(BOOTS_ID, entries)).isEqualByComparingTo("6");
  }

  @Test
  void matchesItemIdentifiersByValueRatherThanObjectReference() {
    ItemId requested = new ItemId(UUID.fromString(OIL_ID.value().toString()));
    StockEntry entry = entry(101, line(OIL_ID, "1.50"));

    assertThat(calculator.calculate(requested, List.of(entry))).isEqualByComparingTo("1.50");
  }

  @ParameterizedTest
  @CsvSource({
    "0.10, 0.20, 0.30",
    "1000000000000000000000000.000001, 0.000009, 1000000000000000000000000.000010",
    "0.000000000001, 0.000000000002, 0.000000000003"
  })
  void sumsFractionsAndLargeMagnitudesExactly(String first, String second, String expected) {
    List<StockEntry> entries =
        List.of(entry(101, line(OIL_ID, first)), entry(102, line(OIL_ID, second)));

    assertThat(calculator.calculate(OIL_ID, entries)).isEqualByComparingTo(expected);
  }

  @Test
  void preservesASingleQuantityAtTheMinimumDecimalScale() {
    BigDecimal value = new BigDecimal(BigInteger.TEN, Integer.MIN_VALUE);
    StockEntryLine line = new StockEntryLine(OIL_ID, MovementQuantity.of(value));

    assertThat(calculator.calculate(OIL_ID, List.of(entry(101, line)))).isEqualByComparingTo(value);
  }

  @Test
  void rejectsAMissingItemIdentifierEvenWithAnEmptyHistory() {
    assertThatNullPointerException()
        .isThrownBy(() -> calculator.calculate(null, List.of()))
        .withMessage("Item ID is required");
  }

  @Test
  void rejectsAMissingHistory() {
    assertThatNullPointerException()
        .isThrownBy(() -> calculator.calculate(OIL_ID, null))
        .withMessage("Stock entries are required");
  }

  @Test
  void rejectsNullEntriesAfterAMatchingEntry() {
    List<StockEntry> entries = Arrays.asList(entry(101, line(OIL_ID, "1.50")), null);

    assertThatNullPointerException()
        .isThrownBy(() -> calculator.calculate(OIL_ID, entries))
        .withMessage("Stock entry is required");
  }

  @Test
  void rejectsNullEntriesEvenWhenTheItemDoesNotMatch() {
    List<StockEntry> entries = Arrays.asList(entry(101, line(BOOTS_ID, "4")), null);

    assertThatNullPointerException()
        .isThrownBy(() -> calculator.calculate(OIL_ID, entries))
        .withMessage("Stock entry is required");
  }

  @Test
  void rejectsTheSameEntryObjectTwice() {
    StockEntry entry = entry(101, line(OIL_ID, "1.50"));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> calculator.calculate(OIL_ID, List.of(entry, entry)))
        .withMessage("Stock entry history must contain each entry at most once");
  }

  @Test
  void rejectsEqualEntryIdentifiersWithDifferentContents() {
    StockEntry first = entry(101, line(OIL_ID, "1.50"));
    StockEntry second =
        new StockEntry(
            new StockEntryId(UUID.fromString(first.id().value().toString())),
            OCCURRED_AT.plusSeconds(60),
            List.of(line(OIL_ID, "9")));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> calculator.calculate(OIL_ID, List.of(first, second)))
        .withMessage("Stock entry history must contain each entry at most once");
  }

  @Test
  void rejectsRepeatedEntryIdentifiersEvenWhenTheItemDoesNotMatch() {
    StockEntry first = entry(101, line(BOOTS_ID, "4"));
    StockEntry second = entry(101, line(BOOTS_ID, "2"));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> calculator.calculate(OIL_ID, List.of(first, second)))
        .withMessage("Stock entry history must contain each entry at most once");
  }

  @Test
  void countsIdenticalContentsWhenTheEntryIdentifiersDiffer() {
    StockEntry first = entry(101, line(OIL_ID, "1.50"));
    StockEntry second = entry(102, line(OIL_ID, "1.50"));

    assertThat(calculator.calculate(OIL_ID, List.of(first, second))).isEqualByComparingTo("3");
  }

  @Test
  void derivesTheSameNumericTotalInDifferentOrdersWithoutChangingTheInputs() {
    StockEntry first = entry(101, line(OIL_ID, "1.500"), line(BOOTS_ID, "4"));
    StockEntry second = entry(102, line(OIL_ID, "0.75"));
    List<StockEntry> entries = new ArrayList<>(List.of(second, first));

    BigDecimal balance = calculator.calculate(OIL_ID, entries);
    BigDecimal reorderedBalance = calculator.calculate(OIL_ID, List.of(first, second));

    assertThat(balance).isEqualByComparingTo("2.25").isEqualByComparingTo(reorderedBalance);
    assertThat(entries).containsExactly(second, first);
    assertThat(first.lines().getFirst().quantity().value())
        .isEqualByComparingTo("1.500")
        .hasScaleOf(3);
    assertThat(second.lines().getFirst().quantity().value())
        .isEqualByComparingTo("0.75")
        .hasScaleOf(2);
    assertThat(first.occurredAt()).isEqualTo(OCCURRED_AT);
    assertThat(second.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void includesAllSuppliedDatesWithoutAnImplicitCurrentTimeCutoff() {
    StockEntry past =
        new StockEntry(
            new StockEntryId(new UUID(0, 101)),
            Instant.parse("2020-01-01T00:00:00Z"),
            List.of(line(OIL_ID, "1.50")));
    StockEntry future =
        new StockEntry(
            new StockEntryId(new UUID(0, 102)),
            Instant.parse("2099-12-31T23:59:59Z"),
            List.of(line(OIL_ID, "0.75")));

    assertThat(calculator.calculate(OIL_ID, List.of(past, future))).isEqualByComparingTo("2.25");
  }

  @Test
  void keepsNoBalanceOrSeenIdentifiersBetweenCalls() {
    StockEntry entry = entry(101, line(OIL_ID, "1.50"));

    assertThat(calculator.calculate(OIL_ID, List.of(entry))).isEqualByComparingTo("1.50");
    assertThat(calculator.calculate(OIL_ID, List.of(entry))).isEqualByComparingTo("1.50");
    assertThat(calculator.calculate(BOOTS_ID, List.of(entry))).isZero();
    assertThat(calculator.calculate(OIL_ID, List.of())).isZero();
  }

  private static StockEntry entry(long id, StockEntryLine... lines) {
    return new StockEntry(new StockEntryId(new UUID(0, id)), OCCURRED_AT, List.of(lines));
  }

  private static StockEntryLine line(ItemId itemId, String quantity) {
    return new StockEntryLine(itemId, MovementQuantity.of(new BigDecimal(quantity)));
  }
}
