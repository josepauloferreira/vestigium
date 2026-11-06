package io.github.josepauloferreira.vestigium.infrastructure.web.balance;

import io.github.josepauloferreira.vestigium.application.balance.ItemStockQueries;
import io.github.josepauloferreira.vestigium.domain.item.PhysicalUnit;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ItemEntryHistoryResponse(UUID itemId, PhysicalUnit unit, List<Entry> entries) {
  static ItemEntryHistoryResponse from(ItemStockQueries.History history) {
    return new ItemEntryHistoryResponse(
        history.itemId().value(),
        history.unit(),
        history.entries().stream().map(Entry::from).toList());
  }

  public record Entry(UUID entryId, String occurredAt, BigDecimal quantity) {
    private static Entry from(ItemStockQueries.Receipt receipt) {
      return new Entry(
          receipt.entryId().value(), receipt.occurredAt().toString(), receipt.quantity());
    }
  }
}
