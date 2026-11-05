package io.github.josepauloferreira.vestigium.infrastructure.web.item;

import io.github.josepauloferreira.vestigium.domain.item.Item;
import io.github.josepauloferreira.vestigium.domain.item.PhysicalUnit;
import java.util.UUID;

public record ItemResponse(UUID id, String name, PhysicalUnit unit) {

  static ItemResponse from(Item item) {
    return new ItemResponse(item.id().value(), item.name(), item.unit());
  }
}
