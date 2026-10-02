package io.github.josepauloferreira.vestigium.stock;

import static io.github.josepauloferreira.vestigium.stock.MovementEffect.DECREASE;
import static io.github.josepauloferreira.vestigium.stock.MovementEffect.INCREASE;
import static io.github.josepauloferreira.vestigium.stock.MovementType.ADJUSTMENT;
import static io.github.josepauloferreira.vestigium.stock.MovementType.ENTRY;
import static io.github.josepauloferreira.vestigium.stock.MovementType.EXIT;
import static io.github.josepauloferreira.vestigium.stock.MovementType.INITIAL_STOCK;
import static io.github.josepauloferreira.vestigium.stock.MovementType.RETURN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class StockBalanceCalculatorTest {

  private static final Item GLOVES = new Item("Luva de estria");
  private static final Item BOOTS = new Item("Bota 39");

  @Test
  void derivesBalanceFromRegularMovementTypes() {
    var history =
        List.of(
            movement(INITIAL_STOCK, line(GLOVES, 100)),
            movement(ENTRY, line(GLOVES, 10)),
            movement(EXIT, line(GLOVES, 5)),
            movement(RETURN, line(GLOVES, 2)));

    assertEquals(107, StockBalanceCalculator.calculate(GLOVES, history));
  }

  @Test
  void appliesAdjustmentEffectPerLine() {
    var history =
        List.of(
            movement(INITIAL_STOCK, line(GLOVES, 100), line(BOOTS, 50)),
            movement(
                ADJUSTMENT,
                adjustment(GLOVES, 5, INCREASE),
                adjustment(BOOTS, 2, DECREASE)));

    assertEquals(105, StockBalanceCalculator.calculate(GLOVES, history));
    assertEquals(48, StockBalanceCalculator.calculate(BOOTS, history));
  }

  @Test
  void derivesBalanceOnlyFromLinesForRequestedItem() {
    var history =
        List.of(
            movement(INITIAL_STOCK, line(GLOVES, 100), line(BOOTS, 50)),
            movement(EXIT, line(GLOVES, 10), line(BOOTS, 20)));

    assertEquals(90, StockBalanceCalculator.calculate(GLOVES, history));
    assertEquals(30, StockBalanceCalculator.calculate(BOOTS, history));
  }

  @Test
  void rejectsNonPositiveMovementQuantity() {
    assertThrows(IllegalArgumentException.class, () -> line(GLOVES, 0));
    assertThrows(IllegalArgumentException.class, () -> line(GLOVES, -1));
  }

  @Test
  void requiresExplicitEffectForAdjustmentLines() {
    assertThrows(
        IllegalArgumentException.class,
        () -> movement(ADJUSTMENT, line(GLOVES, 5)));
  }

  @Test
  void rejectsExplicitAdjustmentEffectForFixedMovementTypes() {
    assertThrows(
        IllegalArgumentException.class,
        () -> movement(EXIT, adjustment(GLOVES, 5, INCREASE)));
  }

  private static StockMovement movement(MovementType type, MovementLine... lines) {
    return new StockMovement(type, List.of(lines));
  }

  private static MovementLine line(Item item, int quantity) {
    return new MovementLine(item, quantity);
  }

  private static MovementLine adjustment(Item item, int quantity, MovementEffect effect) {
    return new MovementLine(item, quantity, effect);
  }
}
