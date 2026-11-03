package io.github.josepauloferreira.vestigium.infrastructure.persistence;

import io.github.josepauloferreira.vestigium.application.DuplicateStockEntryIdException;
import io.github.josepauloferreira.vestigium.application.StockEntryRepository;
import io.github.josepauloferreira.vestigium.domain.StockEntry;
import io.github.josepauloferreira.vestigium.domain.StockEntryId;
import jakarta.persistence.EntityExistsException;
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

  @Override
  @Transactional
  public void insert(StockEntry entry) {
    Objects.requireNonNull(entry, "Stock entry is required");
    try {
      repository.saveAndFlush(new StockEntryEntity(entry));
    } catch (DataIntegrityViolationException exception) {
      if (isDuplicateIdentity(exception)) {
        throw new DuplicateStockEntryIdException(entry.id(), exception);
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
}
