package io.github.josepauloferreira.vestigium.application.stockentry;

public class InvalidStockEntryDataException extends RuntimeException {

  public InvalidStockEntryDataException(String message) {
    super(message);
  }

  public InvalidStockEntryDataException(String message, Throwable cause) {
    super(message, cause);
  }
}
