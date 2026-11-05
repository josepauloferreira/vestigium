package io.github.josepauloferreira.vestigium.infrastructure.persistence.stockentry;

import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntry;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntryId;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "stock_entries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockEntryEntity implements Persistable<UUID> {

  @Id @Getter private UUID id;
  private Instant occurredAt;

  @ElementCollection
  @CollectionTable(name = "stock_entry_lines", joinColumns = @JoinColumn(name = "entry_id"))
  @OrderColumn(name = "line_number")
  private List<StockEntryLineValue> lines = new ArrayList<>();

  @Transient private boolean newEntity = true;

  StockEntryEntity(StockEntry entry) {
    id = entry.id().value();
    occurredAt = entry.occurredAt();
    entry.lines().stream().map(StockEntryLineValue::new).forEach(lines::add);
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  void markPersisted() {
    newEntity = false;
  }

  StockEntry toDomain() {
    return new StockEntry(
        new StockEntryId(id),
        occurredAt,
        lines.stream().map(StockEntryLineValue::toDomain).toList());
  }
}
