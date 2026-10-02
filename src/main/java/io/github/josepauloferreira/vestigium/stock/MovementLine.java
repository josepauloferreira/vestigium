package io.github.josepauloferreira.vestigium.stock;

import java.util.Objects;

public record MovementLine(Item item, int quantity, MovementEffect adjustmentEffect) {

  public MovementLine(Item item, int quantity) {
    this(item, quantity, null);
  }

  public MovementLine {
    Objects.requireNonNull(item, "item");

    if (quantity <= 0) {
      throw new IllegalArgumentException("Movement quantity must be positive");
    }
  }
}
