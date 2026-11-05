package io.github.josepauloferreira.vestigium.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

interface SpringDataInitialStockRepository extends Repository<InitialStockEntity, UUID> {
  <S extends InitialStockEntity> S saveAndFlush(S entity);

  Optional<InitialStockEntity> findById(UUID itemId);
}
