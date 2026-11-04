package io.github.josepauloferreira.vestigium.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface SpringDataStockEntryRepository extends Repository<StockEntryEntity, UUID> {

  <S extends StockEntryEntity> S saveAndFlush(S entity);

  Optional<StockEntryEntity> findById(UUID id);

  @Query(
      """
      select entry from StockEntryEntity entry
      join fetch entry.lines
      where exists (
        select 1 from StockEntryEntity matching join matching.lines line
        where matching.id = entry.id and line.itemId = :itemId
      )
      order by entry.occurredAt, entry.id
      """)
  List<StockEntryEntity> findByItemId(@Param("itemId") UUID itemId);
}
