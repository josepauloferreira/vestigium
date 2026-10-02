package io.github.josepauloferreira.vestigium.stock;

import java.util.List;
import java.util.Objects;

public final class StockBalanceCalculator {

  private StockBalanceCalculator() {}

  public static int calculate(Item item, List<StockMovement> history) {
    Objects.requireNonNull(item, "item");
    Objects.requireNonNull(history, "history");

    return history.stream()
        .flatMapToInt(
            movement ->
                movement.lines().stream()
                    .filter(line -> line.item().equals(item))
                    .mapToInt(movement::effectOf))
        .sum();
  }
}
