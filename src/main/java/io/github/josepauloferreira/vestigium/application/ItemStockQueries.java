package io.github.josepauloferreira.vestigium.application;

import io.github.josepauloferreira.vestigium.domain.ItemId;
import io.github.josepauloferreira.vestigium.domain.PhysicalUnit;
import io.github.josepauloferreira.vestigium.domain.StockBalanceCalculator;
import io.github.josepauloferreira.vestigium.domain.StockEntryId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ItemStockQueries {
  private final ItemRepository items;
  private final StockEntryRepository entries;

  public ItemStockQueries(ItemRepository items, StockEntryRepository entries) {
    this.items = items;
    this.entries = entries;
  }

  public Balance balance(ItemId itemId) {
    var item = items.findById(itemId).orElseThrow(ItemNotFoundException::new);
    var history = entries.findByItemId(itemId);
    return new Balance(
        itemId, item.unit(), new StockBalanceCalculator().calculate(itemId, history));
  }

  public record Balance(ItemId itemId, PhysicalUnit unit, BigDecimal quantity) {}

  public History history(ItemId itemId) {
    var item = items.findById(itemId).orElseThrow(ItemNotFoundException::new);
    var receipts =
        entries.findByItemId(itemId).stream()
            .map(
                entry ->
                    new Receipt(
                        entry.id(),
                        entry.occurredAt(),
                        entry.lines().stream()
                            .filter(line -> itemId.equals(line.itemId()))
                            .findFirst()
                            .orElseThrow()
                            .quantity()
                            .value()))
            .toList();
    return new History(itemId, item.unit(), receipts);
  }

  public record History(ItemId itemId, PhysicalUnit unit, List<Receipt> entries) {}

  public record Receipt(StockEntryId entryId, Instant occurredAt, BigDecimal quantity) {}
}
