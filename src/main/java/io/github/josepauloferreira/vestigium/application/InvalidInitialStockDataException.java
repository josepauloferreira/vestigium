package io.github.josepauloferreira.vestigium.application;

public class InvalidInitialStockDataException extends RuntimeException {
  public InvalidInitialStockDataException(String message) {
    super(message);
  }

  public InvalidInitialStockDataException(String message, Throwable cause) {
    super(message, cause);
  }
}
