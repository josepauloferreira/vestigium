package io.github.josepauloferreira.vestigium.infrastructure.web;

import io.github.josepauloferreira.vestigium.domain.StockEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StockEntryResponse(UUID id, String occurredAt, List<Line> lines) {

  static StockEntryResponse from(StockEntry entry) {
    return new StockEntryResponse(
        entry.id().value(),
        entry.occurredAt().toString(),
        entry.lines().stream()
            .map(line -> new Line(line.itemId().value(), line.quantity().value()))
            .toList());
  }

  @Schema(name = "StockEntryResponseLine")
  public record Line(UUID itemId, BigDecimal quantity) {}
}
