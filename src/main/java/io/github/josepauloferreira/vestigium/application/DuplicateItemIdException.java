package io.github.josepauloferreira.vestigium.application;

import io.github.josepauloferreira.vestigium.domain.ItemId;

public class DuplicateItemIdException extends RuntimeException {

  public DuplicateItemIdException(ItemId id, Throwable cause) {
    super("An item with ID " + id.value() + " already exists", cause);
  }
}
