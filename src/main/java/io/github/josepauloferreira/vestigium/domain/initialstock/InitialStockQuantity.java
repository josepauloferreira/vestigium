package io.github.josepauloferreira.vestigium.domain.initialstock;

import java.math.BigDecimal;
import java.util.Objects;

public record InitialStockQuantity(BigDecimal value) {
  public InitialStockQuantity {
    Objects.requireNonNull(value, "Initial stock quantity is required");
    if (value.signum() < 0) {
      throw new IllegalArgumentException("Initial stock quantity must not be negative");
    }
  }

  public static InitialStockQuantity of(BigDecimal value) {
    return new InitialStockQuantity(value);
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof InitialStockQuantity(BigDecimal otherValue)
            && value.compareTo(otherValue) == 0;
  }

  @Override
  public int hashCode() {
    if (value.signum() == 0) return 0;
    BigDecimal coefficient = new BigDecimal(value.unscaledValue()).stripTrailingZeros();
    long normalizedScale = (long) value.scale() + coefficient.scale();
    return 31 * coefficient.unscaledValue().hashCode() + Long.hashCode(normalizedScale);
  }
}
