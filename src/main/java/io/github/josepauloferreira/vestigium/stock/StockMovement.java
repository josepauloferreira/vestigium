package io.github.josepauloferreira.vestigium.stock;

import java.util.List;
import java.util.Objects;

public record StockMovement(MovementType type, List<MovementLine> lines) {

  public StockMovement {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(lines, "lines");

    lines = List.copyOf(lines);

    if (lines.isEmpty()) {
      throw new IllegalArgumentException("A stock movement must contain at least one line");
    }

    for (var line : lines) {
      var hasExplicitEffect = line.adjustmentEffect() != null;

      if (type == MovementType.ADJUSTMENT && !hasExplicitEffect) {
        throw new IllegalArgumentException("ADJUSTMENT lines require an explicit effect");
      }

      if (type != MovementType.ADJUSTMENT && hasExplicitEffect) {
        throw new IllegalArgumentException(
            "Only ADJUSTMENT lines may define an explicit effect");
      }
    }
  }

  int effectOf(MovementLine line) {
    return type.resolveEffect(line.adjustmentEffect()).apply(line.quantity());
  }
}
