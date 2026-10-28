package io.github.josepauloferreira.vestigium.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record MovementQuantity(BigDecimal value) {
  public MovementQuantity {
    Objects.requireNonNull(value, "Movement quantity is required");
    if (value.signum() <= 0) {
      throw new IllegalArgumentException("Movement quantity must be greater than zero");
    }
  }

  public static MovementQuantity of(BigDecimal value) {
    return new MovementQuantity(value);
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof MovementQuantity(BigDecimal otherValue)
            && value.compareTo(otherValue) == 0;
  }

  @Override
  public int hashCode() {
    BigDecimal coefficient = new BigDecimal(value.unscaledValue()).stripTrailingZeros();
    long normalizedScale = (long) value.scale() + coefficient.scale();
    return 31 * coefficient.unscaledValue().hashCode() + Long.hashCode(normalizedScale);
  }
}
