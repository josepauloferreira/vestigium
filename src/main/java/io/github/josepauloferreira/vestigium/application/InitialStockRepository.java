package io.github.josepauloferreira.vestigium.application;

import io.github.josepauloferreira.vestigium.domain.InitialStock;
import io.github.josepauloferreira.vestigium.domain.ItemId;
import java.util.Optional;

public interface InitialStockRepository {
  void insert(InitialStock stock);

  Optional<InitialStock> findByItemId(ItemId itemId);
}
