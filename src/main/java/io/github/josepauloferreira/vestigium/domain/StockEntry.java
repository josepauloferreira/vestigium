package io.github.josepauloferreira.vestigium.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public final class StockEntry {
  private final StockEntryId id;
  private final Instant occurredAt;
  private final List<StockEntryLine> lines;

  public StockEntry(StockEntryId id, Instant occurredAt, List<StockEntryLine> lines) {
    this.id = Objects.requireNonNull(id, "Stock entry ID is required");
    this.occurredAt = Objects.requireNonNull(occurredAt, "Stock entry occurrence time is required");
    this.lines = List.copyOf(Objects.requireNonNull(lines, "Stock entry lines are required"));

    if (this.lines.isEmpty()) {
      throw new IllegalArgumentException("Stock entry must contain at least one line");
    }

    Set<ItemId> itemIds = new HashSet<>();

    for (StockEntryLine line : this.lines) {
      if (!itemIds.add(line.itemId())) {
        throw new IllegalArgumentException("Stock entry must contain each item at most once");
      }
    }
  }

  @Override
  public boolean equals(Object other) {
    return this == other || other instanceof StockEntry entry && id.equals(entry.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
