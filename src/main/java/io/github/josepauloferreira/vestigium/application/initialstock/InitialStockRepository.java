package io.github.josepauloferreira.vestigium.application.initialstock;

import io.github.josepauloferreira.vestigium.domain.initialstock.InitialStock;
import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import java.util.Optional;

public interface InitialStockRepository {
  void insert(InitialStock stock);

  Optional<InitialStock> findByItemId(ItemId itemId);
}
