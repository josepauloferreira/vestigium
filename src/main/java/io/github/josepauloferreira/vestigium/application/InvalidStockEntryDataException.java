package io.github.josepauloferreira.vestigium.application;

public class InvalidStockEntryDataException extends RuntimeException {

  public InvalidStockEntryDataException(String message) {
    super(message);
  }

  public InvalidStockEntryDataException(String message, Throwable cause) {
    super(message, cause);
  }
}
