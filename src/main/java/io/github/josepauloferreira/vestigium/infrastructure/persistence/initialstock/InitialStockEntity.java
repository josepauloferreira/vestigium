package io.github.josepauloferreira.vestigium.infrastructure.persistence.initialstock;

import io.github.josepauloferreira.vestigium.application.initialstock.InvalidInitialStockDataException;
import io.github.josepauloferreira.vestigium.domain.initialstock.InitialStock;
import io.github.josepauloferreira.vestigium.domain.initialstock.InitialStockQuantity;
import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "initial_stock")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InitialStockEntity implements Persistable<UUID> {
  @Id private UUID itemId;
  private BigDecimal quantity;
  private int quantityScale;
  private Instant occurredAt;
  @Transient private boolean newEntity = true;

  InitialStockEntity(InitialStock stock) {
    itemId = stock.itemId().value();
    quantityScale = stock.quantity().value().scale();
    quantity = supported(stock.quantity().value());
    occurredAt = stock.occurredAt();
    try {
      occurredAt.toEpochMilli();
    } catch (ArithmeticException exception) {
      throw new InvalidInitialStockDataException(
          "occurredAt exceeds supported JDBC timestamp limits", exception);
    }
  }

  private static BigDecimal supported(BigDecimal supplied) {
    if (supplied.signum() == 0) return BigDecimal.ZERO;
    long integerDigits = (long) supplied.precision() - supplied.scale();
    BigDecimal exact = supplied.scale() > 16_383 ? supplied.stripTrailingZeros() : supplied;
    if (integerDigits > 131_072 || exact.scale() > 16_383) {
      throw new InvalidInitialStockDataException(
          "Initial stock quantity exceeds PostgreSQL NUMERIC storage limits");
    }
    return exact;
  }

  @Override
  public UUID getId() {
    return itemId;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  void markPersisted() {
    newEntity = false;
  }

  InitialStock toDomain() {
    return new InitialStock(
        new ItemId(itemId),
        new InitialStockQuantity(quantity.setScale(quantityScale, RoundingMode.UNNECESSARY)),
        occurredAt);
  }
}
