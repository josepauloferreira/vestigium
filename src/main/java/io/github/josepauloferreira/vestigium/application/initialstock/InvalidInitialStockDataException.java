package io.github.josepauloferreira.vestigium.application.initialstock;

public class InvalidInitialStockDataException extends RuntimeException {
  public InvalidInitialStockDataException(String message) {
    super(message);
  }

  public InvalidInitialStockDataException(String message, Throwable cause) {
    super(message, cause);
  }
}
