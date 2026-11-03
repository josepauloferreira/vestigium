package io.github.josepauloferreira.vestigium.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

interface SpringDataStockEntryRepository extends Repository<StockEntryEntity, UUID> {

  <S extends StockEntryEntity> S saveAndFlush(S entity);

  Optional<StockEntryEntity> findById(UUID id);
}
