package io.github.josepauloferreira.vestigium.application;

import io.github.josepauloferreira.vestigium.domain.StockEntryId;

public class DuplicateStockEntryIdException extends RuntimeException {

  public DuplicateStockEntryIdException(StockEntryId id, Throwable cause) {
    super("A stock entry with ID " + id.value() + " already exists", cause);
  }
}
