package io.github.josepauloferreira.vestigium.domain.initialstock;

import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public final class InitialStock {
  private final ItemId itemId;
  private final InitialStockQuantity quantity;
  private final Instant occurredAt;

  public InitialStock(ItemId itemId, InitialStockQuantity quantity, Instant occurredAt) {
    this.itemId = Objects.requireNonNull(itemId, "Item ID is required");
    this.quantity = Objects.requireNonNull(quantity, "Initial stock quantity is required");
    this.occurredAt = Objects.requireNonNull(occurredAt, "Initial stock occurrence is required");
    if (occurredAt.getNano() % 1_000 != 0) {
      throw new IllegalArgumentException("Initial stock occurrence must use microsecond precision");
    }
  }

  public boolean precedesOrEquals(Instant receiptTime) {
    return !occurredAt.isAfter(Objects.requireNonNull(receiptTime, "Receipt time is required"));
  }

  @Override
  public boolean equals(Object other) {
    return this == other || other instanceof InitialStock stock && itemId.equals(stock.itemId);
  }

  @Override
  public int hashCode() {
    return itemId.hashCode();
  }
}
