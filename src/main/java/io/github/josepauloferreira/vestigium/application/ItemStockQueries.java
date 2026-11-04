package io.github.josepauloferreira.vestigium.application;

import io.github.josepauloferreira.vestigium.domain.ItemId;
import io.github.josepauloferreira.vestigium.domain.PhysicalUnit;
import io.github.josepauloferreira.vestigium.domain.StockBalanceCalculator;
import java.math.BigDecimal;

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
}
