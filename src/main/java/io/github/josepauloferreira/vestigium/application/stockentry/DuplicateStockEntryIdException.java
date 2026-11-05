package io.github.josepauloferreira.vestigium.application.stockentry;

import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntryId;

public class DuplicateStockEntryIdException extends RuntimeException {

  public DuplicateStockEntryIdException(StockEntryId id, Throwable cause) {
    super("A stock entry with ID " + id.value() + " already exists", cause);
  }
}
