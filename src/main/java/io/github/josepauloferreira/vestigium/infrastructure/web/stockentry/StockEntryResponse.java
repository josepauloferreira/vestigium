package io.github.josepauloferreira.vestigium.infrastructure.web.stockentry;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StockEntryResponse(UUID id, String occurredAt, List<Line> lines) {
  @Schema(name = "StockEntryResponseLine")
  public record Line(UUID itemId, BigDecimal quantity) {}
}
