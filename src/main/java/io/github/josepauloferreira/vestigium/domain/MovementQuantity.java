package io.github.josepauloferreira.vestigium.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** A strictly positive physical movement magnitude, preserving its decimal representation. */
public final class MovementQuantity {
  private final BigDecimal value;

  private MovementQuantity(BigDecimal value) {
    this.value = value;
  }

  public static MovementQuantity of(BigDecimal value) {
    Objects.requireNonNull(value, "Movement quantity is required");
    if (value.signum() <= 0) {
      throw new IllegalArgumentException("Movement quantity must be greater than zero");
    }
    return new MovementQuantity(value);
  }

  public BigDecimal value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof MovementQuantity quantity && value.compareTo(quantity.value) == 0;
  }

  @Override
  public int hashCode() {
    // Normalize at scale zero to avoid overflowing an extreme input scale.
    BigDecimal coefficient = new BigDecimal(value.unscaledValue()).stripTrailingZeros();
    long normalizedScale = (long) value.scale() + coefficient.scale();
    return 31 * coefficient.unscaledValue().hashCode() + Long.hashCode(normalizedScale);
  }
}
