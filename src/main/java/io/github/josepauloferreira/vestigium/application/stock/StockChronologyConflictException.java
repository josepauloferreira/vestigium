package io.github.josepauloferreira.vestigium.application.stock;

public class StockChronologyConflictException extends RuntimeException {
  public StockChronologyConflictException() {
    super("Receipt cannot precede initial stock");
  }
}
