package io.github.josepauloferreira.vestigium.application.balance;

import io.github.josepauloferreira.vestigium.application.initialstock.InitialStockRepository;
import io.github.josepauloferreira.vestigium.application.item.ItemNotFoundException;
import io.github.josepauloferreira.vestigium.application.item.ItemRepository;
import io.github.josepauloferreira.vestigium.application.stockentry.StockEntryRepository;
import io.github.josepauloferreira.vestigium.domain.balance.StockBalanceCalculator;
import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import io.github.josepauloferreira.vestigium.domain.item.PhysicalUnit;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntry;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntryId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ItemStockQueries {
  private final ItemRepository items;
  private final StockEntryRepository entries;
  private final InitialStockRepository initialStocks;

  public ItemStockQueries(
      ItemRepository items, StockEntryRepository entries, InitialStockRepository initialStocks) {
    this.items = items;
    this.entries = entries;
    this.initialStocks = initialStocks;
  }

  public Balance balance(ItemId itemId) {
    var item = items.findById(itemId).orElseThrow(ItemNotFoundException::new);
    var history = entries.findByItemId(itemId);
    var calculator = new StockBalanceCalculator();
    var quantity =
        initialStocks
            .findByItemId(itemId)
            .map(initial -> calculator.calculate(itemId, initial, history))
            .orElseGet(() -> calculator.calculate(itemId, history));
    return new Balance(itemId, item.unit(), quantity);
  }

  public History history(ItemId itemId) {
    var item = items.findById(itemId).orElseThrow(ItemNotFoundException::new);
    var receipts =
        entries.findByItemId(itemId).stream().map(entry -> receiptFor(itemId, entry)).toList();
    return new History(itemId, item.unit(), receipts);
  }

  private static Receipt receiptFor(ItemId itemId, StockEntry entry) {
    var line =
        entry.lines().stream()
            .filter(candidate -> itemId.equals(candidate.itemId()))
            .findFirst()
            .orElseThrow();
    return new Receipt(entry.id(), entry.occurredAt(), line.quantity().value());
  }

  public record Balance(ItemId itemId, PhysicalUnit unit, BigDecimal quantity) {}

  public record History(ItemId itemId, PhysicalUnit unit, List<Receipt> entries) {}

  public record Receipt(StockEntryId entryId, Instant occurredAt, BigDecimal quantity) {}
}
