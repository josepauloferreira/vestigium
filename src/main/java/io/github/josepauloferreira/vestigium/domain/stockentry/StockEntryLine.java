package io.github.josepauloferreira.vestigium.domain.stockentry;

import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import io.github.josepauloferreira.vestigium.domain.stock.MovementQuantity;
import java.util.Objects;

public record StockEntryLine(ItemId itemId, MovementQuantity quantity) {
  public StockEntryLine {
    Objects.requireNonNull(itemId, "Item ID is required");
    Objects.requireNonNull(quantity, "Movement quantity is required");
  }
}
