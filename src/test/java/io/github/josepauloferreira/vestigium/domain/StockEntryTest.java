package io.github.josepauloferreira.vestigium.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StockEntryTest {
  private static final StockEntryId ID =
      new StockEntryId(UUID.fromString("00000000-0000-0000-0000-000000000101"));
  private static final Instant OCCURRED_AT = Instant.parse("2026-10-28T13:15:00.123456789Z");
  private static final ItemId OIL_ID =
      new ItemId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final ItemId BOOTS_ID =
      new ItemId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final StockEntryLine OIL =
      new StockEntryLine(OIL_ID, MovementQuantity.of(new BigDecimal("1.50")));
  private static final StockEntryLine BOOTS =
      new StockEntryLine(BOOTS_ID, MovementQuantity.of(new BigDecimal("4")));

  @Test
  void representsAnEntryWithOneItem() {
    StockEntry entry = new StockEntry(ID, OCCURRED_AT, List.of(OIL));

    assertThat(entry.id()).isEqualTo(ID);
    assertThat(entry.occurredAt()).isEqualTo(OCCURRED_AT);
    assertThat(entry.lines()).containsExactly(OIL);
    assertThat(entry.lines().getFirst().quantity().value())
        .isEqualByComparingTo("1.50")
        .hasScaleOf(2);
  }

  @Test
  void preservesMultipleItemsAndTheirSuppliedOrder() {
    StockEntry entry = new StockEntry(ID, OCCURRED_AT, List.of(BOOTS, OIL));

    assertThat(entry.lines()).containsExactly(BOOTS, OIL);
    assertThat(entry.lines().get(1).quantity().value()).isEqualByComparingTo("1.50").hasScaleOf(2);
  }

  @Test
  void rejectsMissingIdentity() {
    assertThatNullPointerException()
        .isThrownBy(() -> new StockEntry(null, OCCURRED_AT, List.of(OIL)))
        .withMessage("Stock entry ID is required");
  }

  @Test
  void rejectsMissingOccurrenceTime() {
    assertThatNullPointerException()
        .isThrownBy(() -> new StockEntry(ID, null, List.of(OIL)))
        .withMessage("Stock entry occurrence time is required");
  }

  @Test
  void rejectsMissingLines() {
    assertThatNullPointerException()
        .isThrownBy(() -> new StockEntry(ID, OCCURRED_AT, null))
        .withMessage("Stock entry lines are required");
  }

  @Test
  void rejectsAnEmptyEntry() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new StockEntry(ID, OCCURRED_AT, List.of()))
        .withMessage("Stock entry must contain at least one line");
  }

  @Test
  void rejectsNullLines() {
    List<StockEntryLine> lines = Arrays.asList(OIL, null);

    assertThatNullPointerException().isThrownBy(() -> new StockEntry(ID, OCCURRED_AT, lines));
  }

  @Test
  void rejectsTheSameLineTwice() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new StockEntry(ID, OCCURRED_AT, List.of(OIL, OIL)))
        .withMessage("Stock entry must contain each item at most once");
  }

  @Test
  void rejectsRepeatedItemIdentityEvenWithDifferentQuantitiesAndItemIdInstances() {
    StockEntryLine repeatedOil =
        new StockEntryLine(new ItemId(OIL_ID.value()), MovementQuantity.of(new BigDecimal("3")));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new StockEntry(ID, OCCURRED_AT, List.of(OIL, BOOTS, repeatedOil)))
        .withMessage("Stock entry must contain each item at most once");
  }

  @Test
  void keepsTheEntryUnchangedWhenTheCallerReplacesAndClearsItsOriginalList() {
    List<StockEntryLine> suppliedLines = new ArrayList<>(List.of(OIL));
    StockEntry entry = new StockEntry(ID, OCCURRED_AT, suppliedLines);

    suppliedLines.set(0, BOOTS);
    suppliedLines.clear();

    assertThat(entry.lines()).containsExactly(OIL);
  }

  @Test
  void preventsAddingLinesThroughTheAccessor() {
    StockEntry entry = new StockEntry(ID, OCCURRED_AT, List.of(OIL));

    assertThatThrownBy(() -> entry.lines().add(BOOTS))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(entry.lines()).containsExactly(OIL);
  }

  @Test
  void preventsReplacingAndRemovingLinesThroughTheAccessor() {
    StockEntry entry = new StockEntry(ID, OCCURRED_AT, List.of(OIL));

    assertThatThrownBy(() -> entry.lines().set(0, BOOTS))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> entry.lines().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(entry.lines()).containsExactly(OIL);
  }

  @Test
  void identifiesTheSameEntryDespiteDifferentOccurrenceAndLines() {
    StockEntry first = new StockEntry(ID, OCCURRED_AT, List.of(OIL));
    StockEntry second =
        new StockEntry(new StockEntryId(ID.value()), OCCURRED_AT.plusSeconds(60), List.of(BOOTS));

    assertThat(first).isEqualTo(second);
    assertThat(second).isEqualTo(first);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
    assertThat(Set.of(first).contains(second)).isTrue();
  }

  @Test
  void distinguishesSeparateEntriesWithIdenticalContent() {
    StockEntry first = new StockEntry(ID, OCCURRED_AT, List.of(OIL));
    StockEntryId anotherId =
        new StockEntryId(UUID.fromString("00000000-0000-0000-0000-000000000102"));
    StockEntry second = new StockEntry(anotherId, OCCURRED_AT, List.of(OIL));

    assertThat(first).isNotEqualTo(second);
    assertThat(second.lines()).containsExactly(OIL);
  }

  @Test
  void equalsItselfButNotNullOrOtherTypes() {
    StockEntry entry = new StockEntry(ID, OCCURRED_AT, List.of(OIL));

    assertThat(entry).isEqualTo(entry).isNotEqualTo(null).isNotEqualTo(ID);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2020-01-01T00:00:00Z", "2099-12-31T23:59:59.123456789Z"})
  void preservesSuppliedTimesWithoutAnImplicitCurrentTimePolicy(String input) {
    Instant occurredAt = Instant.parse(input);

    StockEntry entry = new StockEntry(ID, occurredAt, List.of(OIL));

    assertThat(entry.occurredAt()).isEqualTo(occurredAt);
  }
}
