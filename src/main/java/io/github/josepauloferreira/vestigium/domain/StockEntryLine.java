package io.github.josepauloferreira.vestigium.domain;

import java.util.Objects;

public record StockEntryLine(ItemId itemId, MovementQuantity quantity) {
  public StockEntryLine {
    Objects.requireNonNull(itemId, "Item ID is required");
    Objects.requireNonNull(quantity, "Movement quantity is required");
  }
}
