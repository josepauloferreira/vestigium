package io.github.josepauloferreira.vestigium.infrastructure.persistence;

import io.github.josepauloferreira.vestigium.application.InvalidStockEntryDataException;
import io.github.josepauloferreira.vestigium.domain.ItemId;
import io.github.josepauloferreira.vestigium.domain.MovementQuantity;
import io.github.josepauloferreira.vestigium.domain.StockEntryLine;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

@Embeddable
record StockEntryLineValue(UUID itemId, BigDecimal quantity, int quantityScale) {

  StockEntryLineValue {
    Objects.requireNonNull(itemId, "Stored item ID is required");
    quantity = supportedQuantity(Objects.requireNonNull(quantity, "Stored quantity is required"));
    if (quantity.signum() <= 0) {
      throw new IllegalArgumentException("Stored quantity must be greater than zero");
    }
    quantity.setScale(quantityScale, RoundingMode.UNNECESSARY);
  }

  StockEntryLineValue(StockEntryLine line) {
    this(line.itemId().value(), line.quantity().value(), line.quantity().value().scale());
  }

  private static BigDecimal supportedQuantity(BigDecimal supplied) {
    long integerDigits = (long) supplied.precision() - supplied.scale();
    if (integerDigits > 131_072) {
      throw new InvalidStockEntryDataException(
          "Stock entry quantity exceeds PostgreSQL NUMERIC storage limits");
    }
    BigDecimal exact = supplied.scale() > 16_383 ? supplied.stripTrailingZeros() : supplied;
    if (exact.scale() > 16_383) {
      throw new InvalidStockEntryDataException(
          "Stock entry quantity exceeds PostgreSQL NUMERIC storage limits");
    }
    return exact;
  }

  StockEntryLine toDomain() {
    BigDecimal original = quantity.setScale(quantityScale, RoundingMode.UNNECESSARY);
    return new StockEntryLine(new ItemId(itemId), new MovementQuantity(original));
  }
}
