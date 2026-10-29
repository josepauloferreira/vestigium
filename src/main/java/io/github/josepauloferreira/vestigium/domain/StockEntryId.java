package io.github.josepauloferreira.vestigium.domain;

import java.util.Objects;
import java.util.UUID;

public record StockEntryId(UUID value) {
  public StockEntryId {
    Objects.requireNonNull(value, "Stock entry ID is required");
  }
}
