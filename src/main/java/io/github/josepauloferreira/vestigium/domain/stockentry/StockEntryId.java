package io.github.josepauloferreira.vestigium.domain.stockentry;

import java.util.Objects;
import java.util.UUID;

public record StockEntryId(UUID value) {
  public StockEntryId {
    Objects.requireNonNull(value, "Stock entry ID is required");
  }
}
