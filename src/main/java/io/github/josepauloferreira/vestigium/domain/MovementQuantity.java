package io.github.josepauloferreira.vestigium.domain;

import java.math.BigDecimal;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class MovementQuantity {
  private final BigDecimal value;

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
    BigDecimal coefficient = new BigDecimal(value.unscaledValue()).stripTrailingZeros();
    long normalizedScale = (long) value.scale() + coefficient.scale();
    return 31 * coefficient.unscaledValue().hashCode() + Long.hashCode(normalizedScale);
  }
}
