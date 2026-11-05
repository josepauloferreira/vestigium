package io.github.josepauloferreira.vestigium.application;

public class DuplicateInitialStockException extends RuntimeException {
  public DuplicateInitialStockException() {
    super("Initial stock already exists for this item");
  }
}
