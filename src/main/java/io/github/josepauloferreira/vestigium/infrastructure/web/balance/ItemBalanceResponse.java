package io.github.josepauloferreira.vestigium.infrastructure.web.balance;

import io.github.josepauloferreira.vestigium.application.balance.ItemStockQueries;
import io.github.josepauloferreira.vestigium.domain.item.PhysicalUnit;
import java.math.BigDecimal;
import java.util.UUID;

public record ItemBalanceResponse(UUID itemId, PhysicalUnit unit, BigDecimal quantity) {
  static ItemBalanceResponse from(ItemStockQueries.Balance balance) {
    return new ItemBalanceResponse(balance.itemId().value(), balance.unit(), balance.quantity());
  }
}
