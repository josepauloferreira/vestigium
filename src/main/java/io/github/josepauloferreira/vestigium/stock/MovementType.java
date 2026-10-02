package io.github.josepauloferreira.vestigium.stock;

import java.util.Objects;

public enum MovementType {
  INITIAL_STOCK,
  ENTRY,
  EXIT,
  RETURN,
  ADJUSTMENT;

  MovementEffect resolveEffect(MovementEffect adjustmentEffect) {
    return switch (this) {
      case INITIAL_STOCK, ENTRY, RETURN -> MovementEffect.INCREASE;
      case EXIT -> MovementEffect.DECREASE;
      case ADJUSTMENT ->
          Objects.requireNonNull(adjustmentEffect, "ADJUSTMENT requires an explicit effect");
    };
  }
}
