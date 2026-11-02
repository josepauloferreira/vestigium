package io.github.josepauloferreira.vestigium.infrastructure.persistence;

import io.github.josepauloferreira.vestigium.domain.Item;
import io.github.josepauloferreira.vestigium.domain.ItemId;
import io.github.josepauloferreira.vestigium.domain.PhysicalUnit;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemEntity implements Persistable<UUID> {

  @Id @Getter private UUID id;

  private String name;

  @Enumerated(EnumType.STRING)
  private PhysicalUnit unit;

  @Transient private boolean newEntity = true;

  ItemEntity(Item item) {
    id = item.id().value();
    name = item.name();
    unit = item.unit();
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

  Item toDomain() {
    return new Item(new ItemId(id), name, unit);
  }
}
