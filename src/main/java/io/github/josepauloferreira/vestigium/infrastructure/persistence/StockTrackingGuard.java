package io.github.josepauloferreira.vestigium.infrastructure.persistence;

import io.github.josepauloferreira.vestigium.application.*;
import io.github.josepauloferreira.vestigium.domain.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class StockTrackingGuard {
  private final EntityManager entityManager;

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

  void beforeOpening(InitialStock stock) {
    if (!lockItems(List.of(stock.itemId().value()))) throw new ItemNotFoundException();
    if (entityManager.find(InitialStockEntity.class, stock.itemId().value()) != null)
      throw new DuplicateInitialStockException();
    Instant earliest =
        entityManager
            .createQuery(
                "select min(e.occurredAt) from StockEntryEntity e join e.lines line where line.itemId = :item",
                Instant.class)
            .setParameter("item", stock.itemId().value())
            .getSingleResult();
    if (earliest != null && !stock.precedesOrEquals(earliest))
      throw new StockChronologyConflictException();
  }

  void beforeReceipt(StockEntry entry) {
    var ids = entry.lines().stream().map(line -> line.itemId().value()).toList();
    if (!lockItems(ids))
      throw new InvalidStockEntryDataException("Every stock entry item must exist in the catalog");
    long conflicts =
        entityManager
            .createQuery(
                "select count(s) from InitialStockEntity s where s.itemId in :ids and s.occurredAt > :time",
                Long.class)
            .setParameter("ids", ids)
            .setParameter("time", entry.occurredAt())
            .getSingleResult();
    if (conflicts > 0) throw new StockChronologyConflictException();
  }
}
