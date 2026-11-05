package io.github.josepauloferreira.vestigium.application.stockentry;

import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntry;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntryId;
import java.util.List;
import java.util.Optional;

public interface StockEntryRepository {

  /** Creates the complete entry atomically; an existing identity is rejected without overwrite. */
  void insert(StockEntry entry);

  Optional<StockEntry> findById(StockEntryId id);

  /**
   * Returns complete receipts containing this item, ordered by occurrence and database UUID order.
   */
  List<StockEntry> findByItemId(ItemId itemId);
}
