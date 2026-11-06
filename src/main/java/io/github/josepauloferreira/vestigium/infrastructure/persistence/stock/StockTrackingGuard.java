package io.github.josepauloferreira.vestigium.infrastructure.persistence.stock;

import io.github.josepauloferreira.vestigium.application.initialstock.DuplicateInitialStockException;
import io.github.josepauloferreira.vestigium.application.item.ItemNotFoundException;
import io.github.josepauloferreira.vestigium.application.stock.StockChronologyConflictException;
import io.github.josepauloferreira.vestigium.application.stockentry.InvalidStockEntryDataException;
import io.github.josepauloferreira.vestigium.domain.initialstock.InitialStock;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntry;
import io.github.josepauloferreira.vestigium.infrastructure.persistence.initialstock.InitialStockEntity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StockTrackingGuard {
  private final EntityManager entityManager;

  public void beforeOpening(InitialStock stock) {
    UUID itemId = stock.itemId().value();
    if (!lockItems(List.of(itemId))) {
      throw new ItemNotFoundException();
    }
    if (entityManager.find(InitialStockEntity.class, itemId) != null) {
      throw new DuplicateInitialStockException();
    }
    Instant earliestReceipt = earliestReceiptFor(itemId);
    if (earliestReceipt != null && !stock.precedesOrEquals(earliestReceipt)) {
      throw new StockChronologyConflictException();
    }
  }

  public void beforeReceipt(StockEntry entry) {
    var itemIds = entry.lines().stream().map(line -> line.itemId().value()).toList();
    if (!lockItems(itemIds)) {
      throw new InvalidStockEntryDataException("Every stock entry item must exist in the catalog");
    }
    if (hasOpeningAfter(itemIds, entry.occurredAt())) {
      throw new StockChronologyConflictException();
    }
  }

  private boolean lockItems(List<UUID> ids) {
    // Stable database UUID order, shared by both write paths. The caller owns the transaction.
    var locked =
        entityManager
            .createNativeQuery(
                "SELECT id FROM items WHERE id IN (:ids) ORDER BY id FOR UPDATE", UUID.class)
            .setParameter("ids", ids)
            .getResultList();
    return locked.size() == ids.size();
  }

  private Instant earliestReceiptFor(UUID itemId) {
    return entityManager
        .createQuery(
            "select min(e.occurredAt) from StockEntryEntity e join e.lines line where line.itemId = :item",
            Instant.class)
        .setParameter("item", itemId)
        .getSingleResult();
  }

  private boolean hasOpeningAfter(List<UUID> itemIds, Instant occurredAt) {
    long conflicts =
        entityManager
            .createQuery(
                "select count(s) from InitialStockEntity s where s.itemId in :ids and s.occurredAt > :time",
                Long.class)
            .setParameter("ids", itemIds)
            .setParameter("time", occurredAt)
            .getSingleResult();
    return conflicts > 0;
  }
}
