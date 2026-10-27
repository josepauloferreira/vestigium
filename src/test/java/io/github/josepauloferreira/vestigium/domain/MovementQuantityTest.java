package io.github.josepauloferreira.vestigium.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MovementQuantityTest {
  @ParameterizedTest
  @ValueSource(strings = {"1", "1.5", "0.75", "0.0001", "1.234567", "1.500",
      "1000000000000000000000000.000001"})
  void preservesPositiveMagnitudeAndOriginalScale(String input) {
    BigDecimal value = new BigDecimal(input);

    MovementQuantity quantity = MovementQuantity.of(value);

    assertEquals(value, quantity.value());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "0.00", "-1", "-0.75", "-0.0001"})
  void rejectsNonpositiveMagnitudes(String input) {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> MovementQuantity.of(new BigDecimal(input)));

    assertEquals("Movement quantity must be greater than zero", exception.getMessage());
  }

  @Test
  void rejectsMissingMagnitude() {
    NullPointerException exception = assertThrows(NullPointerException.class,
        () -> MovementQuantity.of(null));

    assertEquals("Movement quantity is required", exception.getMessage());
  }

  @ParameterizedTest
  @CsvSource({"1.5, 1.50", "1000, 1E+3", "0.000100, 0.0001"})
  void treatsNumericallyEqualMagnitudesAsTheSameValue(String first, String second) {
    MovementQuantity left = MovementQuantity.of(new BigDecimal(first));
    MovementQuantity right = MovementQuantity.of(new BigDecimal(second));

    assertEquals(left, right);
    assertEquals(right, left);
    assertEquals(left.hashCode(), right.hashCode());
    assertTrue(Set.of(left).contains(right));
  }

  @Test
  void supportsEqualValuesAtTheMinimumBigDecimalScale() {
    MovementQuantity first = MovementQuantity.of(new BigDecimal(BigInteger.TEN, Integer.MIN_VALUE));
    MovementQuantity second = MovementQuantity.of(
        new BigDecimal(BigInteger.valueOf(100), Integer.MIN_VALUE + 1));

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertTrue(Set.of(first).contains(second));
  }

  @Test
  void distinguishesDifferentMagnitudes() {
    MovementQuantity first = MovementQuantity.of(new BigDecimal("1.5"));
    MovementQuantity second = MovementQuantity.of(new BigDecimal("1.6"));

    assertNotEquals(first, second);
  }

  @Test
  void equalsItselfButNotNullOrOtherTypes() {
    MovementQuantity quantity = MovementQuantity.of(new BigDecimal("1.5"));

    assertEquals(quantity, quantity);
    assertNotEquals(quantity, null);
    assertNotEquals(quantity, new BigDecimal("1.5"));
  }
}
