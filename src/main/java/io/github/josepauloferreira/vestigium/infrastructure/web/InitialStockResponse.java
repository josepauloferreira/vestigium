package io.github.josepauloferreira.vestigium.infrastructure.web;

import io.github.josepauloferreira.vestigium.application.InitialStockOperations;
import io.github.josepauloferreira.vestigium.domain.PhysicalUnit;
import java.math.BigDecimal;
import java.util.UUID;

public record InitialStockResponse(
    UUID itemId, PhysicalUnit unit, BigDecimal quantity, String occurredAt) {
  static InitialStockResponse from(InitialStockOperations.Snapshot snapshot) {
    var stock = snapshot.stock();
    return new InitialStockResponse(
        stock.itemId().value(),
        snapshot.unit(),
        stock.quantity().value(),
        stock.occurredAt().toString());
  }
}
