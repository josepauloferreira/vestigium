package io.github.josepauloferreira.vestigium.stock;

public enum MovementEffect {
  INCREASE(1),
  DECREASE(-1);

  private final int multiplier;

  MovementEffect(int multiplier) {
    this.multiplier = multiplier;
  }

  int apply(int quantity) {
    return multiplier * quantity;
  }
}
