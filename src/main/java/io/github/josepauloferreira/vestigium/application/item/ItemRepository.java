package io.github.josepauloferreira.vestigium.application.item;

import io.github.josepauloferreira.vestigium.domain.item.Item;
import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import java.util.Optional;

public interface ItemRepository {

  /** Creates an item; an existing identity is rejected without changing the stored item. */
  void insert(Item item);

  Optional<Item> findById(ItemId id);
}
