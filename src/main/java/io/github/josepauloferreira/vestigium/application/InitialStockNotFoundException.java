package io.github.josepauloferreira.vestigium.application;

public class InitialStockNotFoundException extends RuntimeException {
  public InitialStockNotFoundException() {
    super("Initial stock has not been recorded for this item");
  }
}
