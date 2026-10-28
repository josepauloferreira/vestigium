package io.github.josepauloferreira.vestigium.domain;

import java.util.Objects;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public final class Item {
  private final ItemId id;
  private final String name;
  private final PhysicalUnit unit;

  public Item(ItemId id, String name, PhysicalUnit unit) {
    this.id = Objects.requireNonNull(id, "Item ID is required");
    this.name = Objects.requireNonNull(name, "Item name is required");
    if (name.isBlank()) {
      throw new IllegalArgumentException("Item name must not be blank");
    }
    this.unit = Objects.requireNonNull(unit, "Physical unit is required");
  }

  @Override
  public boolean equals(Object other) {
    return this == other || other instanceof Item item && id.equals(item.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
