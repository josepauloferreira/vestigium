package io.github.josepauloferreira.vestigium.infrastructure.persistence.initialstock;

import io.github.josepauloferreira.vestigium.application.initialstock.DuplicateInitialStockException;
import io.github.josepauloferreira.vestigium.application.initialstock.InitialStockRepository;
import io.github.josepauloferreira.vestigium.application.initialstock.InvalidInitialStockDataException;
import io.github.josepauloferreira.vestigium.application.item.ItemNotFoundException;
import io.github.josepauloferreira.vestigium.domain.initialstock.InitialStock;
import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import io.github.josepauloferreira.vestigium.infrastructure.persistence.stock.StockTrackingGuard;
import jakarta.persistence.EntityExistsException;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JpaInitialStockRepository implements InitialStockRepository {
  private final SpringDataInitialStockRepository repository;
  private final StockTrackingGuard tracking;

  @Override
  @Transactional
  public void insert(InitialStock stock) {
    Objects.requireNonNull(stock, "Initial stock is required");
    tracking.beforeOpening(stock);
    try {
      repository.saveAndFlush(new InitialStockEntity(stock));
    } catch (DataIntegrityViolationException exception) {
      throw translateWriteFailure(exception);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<InitialStock> findByItemId(ItemId itemId) {
    Objects.requireNonNull(itemId, "Item ID is required");
    return repository.findById(itemId.value()).map(InitialStockEntity::toDomain);
  }

  private static RuntimeException translateWriteFailure(DataIntegrityViolationException exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof EntityExistsException) {
        return new DuplicateInitialStockException();
      }
      if (cause instanceof ConstraintViolationException violation) {
        if ("23505".equals(violation.getSQLState())
            && "initial_stock_pkey".equals(violation.getConstraintName())) {
          return new DuplicateInitialStockException();
        }
        if ("23503".equals(violation.getSQLState())
            && "initial_stock_item_id_fkey".equals(violation.getConstraintName())) {
          return new ItemNotFoundException();
        }
        if ("23514".equals(violation.getSQLState())
            && "initial_stock_occurred_at_check".equals(violation.getConstraintName())) {
          return new InvalidInitialStockDataException(
              "occurredAt exceeds PostgreSQL timestamp storage limits", exception);
        }
      }
      if (cause instanceof SQLException sql && "22008".equals(sql.getSQLState())) {
        return new InvalidInitialStockDataException(
            "occurredAt exceeds PostgreSQL timestamp storage limits", exception);
      }
    }
    return exception;
  }
}
