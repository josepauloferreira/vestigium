package io.github.josepauloferreira.vestigium.infrastructure.web.balance;

import io.github.josepauloferreira.vestigium.domain.item.PhysicalUnit;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ItemEntryHistoryResponse(UUID itemId, PhysicalUnit unit, List<Entry> entries) {
  public record Entry(UUID entryId, String occurredAt, BigDecimal quantity) {}
}
