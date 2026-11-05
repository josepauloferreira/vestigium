package io.github.josepauloferreira.vestigium.domain.item;

import java.util.Objects;
import java.util.UUID;

public record ItemId(UUID value) {
  public ItemId {
    Objects.requireNonNull(value, "Item ID is required");
  }
}
