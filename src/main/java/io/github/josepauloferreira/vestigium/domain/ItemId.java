package io.github.josepauloferreira.vestigium.domain;

import java.util.Objects;
import java.util.UUID;

public record ItemId(UUID value) {
  public ItemId {
    Objects.requireNonNull(value, "Item ID is required");
  }
}
