package io.github.josepauloferreira.vestigium.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

interface SpringDataItemRepository extends Repository<ItemEntity, UUID> {

  <S extends ItemEntity> S saveAndFlush(S entity);

  Optional<ItemEntity> findById(UUID id);
}
