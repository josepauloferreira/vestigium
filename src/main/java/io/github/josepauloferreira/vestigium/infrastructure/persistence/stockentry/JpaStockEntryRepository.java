package io.github.josepauloferreira.vestigium.infrastructure.persistence.stockentry;

import io.github.josepauloferreira.vestigium.application.stockentry.DuplicateStockEntryIdException;
import io.github.josepauloferreira.vestigium.application.stockentry.InvalidStockEntryDataException;
import io.github.josepauloferreira.vestigium.application.stockentry.StockEntryRepository;
import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntry;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntryId;
import io.github.josepauloferreira.vestigium.infrastructure.persistence.stock.StockTrackingGuard;
import jakarta.persistence.EntityExistsException;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.NonUniqueObjectException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JpaStockEntryRepository implements StockEntryRepository {

  private final SpringDataStockEntryRepository repository;
  private final StockTrackingGuard tracking;

  @Override
  @Transactional
  public void insert(StockEntry entry) {
    Objects.requireNonNull(entry, "Stock entry is required");
    try {
      tracking.beforeReceipt(entry);
      repository.saveAndFlush(new StockEntryEntity(entry));
    } catch (DataIntegrityViolationException exception) {
      if (isDuplicateIdentity(exception)) {
        throw new DuplicateStockEntryIdException(entry.id(), exception);
      }
      for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
        if (cause instanceof ConstraintViolationException violation
            && "23503".equals(violation.getSQLState())
            && "stock_entry_lines_item_id_fkey".equals(violation.getConstraintName())) {
          throw new InvalidStockEntryDataException(
              "Every stock entry item must exist in the catalog", exception);
        }
        if (cause instanceof SQLException sql && "22008".equals(sql.getSQLState())) {
          throw new InvalidStockEntryDataException(
              "occurredAt exceeds PostgreSQL timestamp storage limits", exception);
        }
        if (cause instanceof ConstraintViolationException violation
            && "23514".equals(violation.getSQLState())
            && "stock_entries_occurred_at_check".equals(violation.getConstraintName())) {
          throw new InvalidStockEntryDataException(
              "occurredAt exceeds PostgreSQL timestamp storage limits", exception);
        }
      }
      throw exception;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<StockEntry> findById(StockEntryId id) {
    Objects.requireNonNull(id, "Stock entry ID is required");
    return repository.findById(id.value()).map(StockEntryEntity::toDomain);
  }

  private static boolean isDuplicateIdentity(Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof EntityExistsException) {
        return true;
      }
      if (cause instanceof NonUniqueObjectException duplicate
          && StockEntryEntity.class.getName().equals(duplicate.getEntityName())) {
        return true;
      }
      if (cause instanceof ConstraintViolationException violation
          && "23505".equals(violation.getSQLState())
          && "stock_entries_pkey".equals(violation.getConstraintName())) {
        return true;
      }
    }
    return false;
  }

  @Override
  @Transactional(readOnly = true)
  public List<StockEntry> findByItemId(ItemId itemId) {
    Objects.requireNonNull(itemId, "Item ID is required");
    return repository.findByItemId(itemId.value()).stream()
        .map(StockEntryEntity::toDomain)
        .toList();
  }
}
