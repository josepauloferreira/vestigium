package io.github.josepauloferreira.vestigium.domain;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class StockBalanceCalculator {
  public BigDecimal calculate(ItemId itemId, List<StockEntry> entries) {
    Objects.requireNonNull(itemId, "Item ID is required");
    Objects.requireNonNull(entries, "Stock entries are required");

    Set<StockEntryId> entryIds = new HashSet<>();
    BigDecimal balance = BigDecimal.ZERO;

    for (StockEntry entry : entries) {
      Objects.requireNonNull(entry, "Stock entry is required");
      if (!entryIds.add(entry.id())) {
        throw new IllegalArgumentException(
            "Stock entry history must contain each entry at most once");
      }

      for (StockEntryLine line : entry.lines()) {
        if (itemId.equals(line.itemId())) {
          BigDecimal quantity = line.quantity().value();
          // Keep the first quantity directly: aligning zero can overflow at extreme scales.
          balance = balance.signum() == 0 ? quantity : balance.add(quantity);
        }
      }
    }

    return balance;
  }
}
