package io.github.josepauloferreira.vestigium.application;

import io.github.josepauloferreira.vestigium.domain.Item;
import io.github.josepauloferreira.vestigium.domain.ItemId;
import java.util.Optional;

public interface ItemRepository {

  /** Creates an item; an existing identity is rejected without changing the stored item. */
  void insert(Item item);

  Optional<Item> findById(ItemId id);
}
