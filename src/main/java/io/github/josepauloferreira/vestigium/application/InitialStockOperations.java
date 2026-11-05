package io.github.josepauloferreira.vestigium.application;

import io.github.josepauloferreira.vestigium.domain.*;
import java.time.Instant;

public final class InitialStockOperations {
  private final ItemRepository items;
  private final InitialStockRepository stocks;

  public InitialStockOperations(ItemRepository items, InitialStockRepository stocks) {
    this.items = items;
    this.stocks = stocks;
  }

  public Snapshot register(ItemId id, InitialStockQuantity quantity, Instant occurredAt) {
    var item = items.findById(id).orElseThrow(ItemNotFoundException::new);
    var stock = new InitialStock(id, quantity, occurredAt);
    stocks.insert(stock);
    return new Snapshot(stock, item.unit());
  }

  public Snapshot find(ItemId id) {
    var item = items.findById(id).orElseThrow(ItemNotFoundException::new);
    return new Snapshot(
        stocks.findByItemId(id).orElseThrow(InitialStockNotFoundException::new), item.unit());
  }

  public record Snapshot(InitialStock stock, PhysicalUnit unit) {}
}
