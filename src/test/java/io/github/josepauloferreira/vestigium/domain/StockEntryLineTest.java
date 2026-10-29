package io.github.josepauloferreira.vestigium.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StockEntryLineTest {
  private static final ItemId ITEM_ID =
      new ItemId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

  @ParameterizedTest
  @ValueSource(strings = {"1", "1.50", "0.75", "0.0001"})
  void preservesTheItemAndQuantityRepresentation(String input) {
    BigDecimal value = new BigDecimal(input);

    StockEntryLine line = new StockEntryLine(ITEM_ID, MovementQuantity.of(value));

    assertThat(line.itemId()).isEqualTo(ITEM_ID);
    assertThat(line.quantity().value()).isEqualByComparingTo(value).hasScaleOf(value.scale());
  }

  @Test
  void rejectsMissingItemIdentity() {
    MovementQuantity quantity = MovementQuantity.of(new BigDecimal("1.50"));

    assertThatNullPointerException()
        .isThrownBy(() -> new StockEntryLine(null, quantity))
        .withMessage("Item ID is required");
  }

  @Test
  void rejectsMissingQuantity() {
    assertThatNullPointerException()
        .isThrownBy(() -> new StockEntryLine(ITEM_ID, null))
        .withMessage("Movement quantity is required");
  }

  @Test
  void treatsMatchingItemsAndNumericQuantitiesAsTheSameValue() {
    StockEntryLine first = new StockEntryLine(ITEM_ID, MovementQuantity.of(new BigDecimal("1.50")));
    StockEntryLine second =
        new StockEntryLine(new ItemId(ITEM_ID.value()), MovementQuantity.of(new BigDecimal("1.5")));

    assertThat(first).isEqualTo(second);
    assertThat(second).isEqualTo(first);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
    assertThat(Set.of(first).contains(second)).isTrue();
    assertThat(new StockEntryLine(first.itemId(), first.quantity())).isEqualTo(first);
  }

  @Test
  void distinguishesLinesForDifferentItems() {
    MovementQuantity quantity = MovementQuantity.of(new BigDecimal("1.50"));
    StockEntryLine first = new StockEntryLine(ITEM_ID, quantity);
    ItemId anotherItem = new ItemId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    assertThat(first).isNotEqualTo(new StockEntryLine(anotherItem, quantity));
  }

  @Test
  void distinguishesDifferentQuantitiesOfTheSameItem() {
    StockEntryLine first = new StockEntryLine(ITEM_ID, MovementQuantity.of(new BigDecimal("1.50")));
    StockEntryLine second =
        new StockEntryLine(ITEM_ID, MovementQuantity.of(new BigDecimal("2.50")));

    assertThat(first).isNotEqualTo(second);
  }
}
