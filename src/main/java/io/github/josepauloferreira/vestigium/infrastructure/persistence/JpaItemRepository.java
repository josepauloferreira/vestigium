package io.github.josepauloferreira.vestigium.infrastructure.persistence;

import io.github.josepauloferreira.vestigium.application.DuplicateItemIdException;
import io.github.josepauloferreira.vestigium.application.ItemRepository;
import io.github.josepauloferreira.vestigium.domain.Item;
import io.github.josepauloferreira.vestigium.domain.ItemId;
import jakarta.persistence.EntityExistsException;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.NonUniqueObjectException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaItemRepository implements ItemRepository {

  private final SpringDataItemRepository repository;

  private static boolean isDuplicateIdentity(Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof EntityExistsException) {
        return true;
      }

      if (cause instanceof NonUniqueObjectException duplicate
          && ItemEntity.class.getName().equals(duplicate.getEntityName())) {
        return true;
      }

      if (cause instanceof ConstraintViolationException violation
          && "23505".equals(violation.getSQLState())
          && "items_pkey".equals(violation.getConstraintName())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void insert(Item item) {
    Objects.requireNonNull(item, "Item is required");

    try {
      repository.saveAndFlush(new ItemEntity(item));
    } catch (DataIntegrityViolationException exception) {
      if (isDuplicateIdentity(exception)) {
        throw new DuplicateItemIdException(item.id(), exception);
      }

      throw exception;
    }
  }

  @Override
  public Optional<Item> findById(ItemId id) {
    Objects.requireNonNull(id, "Item ID is required");
    return repository.findById(id.value()).map(ItemEntity::toDomain);
  }
}
