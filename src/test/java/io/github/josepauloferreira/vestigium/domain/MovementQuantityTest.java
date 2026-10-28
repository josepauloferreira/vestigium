package io.github.josepauloferreira.vestigium.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

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

    assertThat(quantity.value())
        .isEqualByComparingTo(value)
        .hasScaleOf(value.scale());
    assertThat(new MovementQuantity(quantity.value())).isEqualTo(quantity);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "0.00", "-1", "-0.75", "-0.0001"})
  void rejectsNonpositiveMagnitudes(String input) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> MovementQuantity.of(new BigDecimal(input)))
        .withMessage("Movement quantity must be greater than zero");
  }

  @Test
  void rejectsMissingMagnitude() {
    assertThatNullPointerException()
        .isThrownBy(() -> MovementQuantity.of(null))
        .withMessage("Movement quantity is required");
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "0.00", "-1", "-0.75", "-0.0001"})
  void rejectsNonpositiveMagnitudesThroughTheConstructor(String input) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new MovementQuantity(new BigDecimal(input)))
        .withMessage("Movement quantity must be greater than zero");
  }

  @Test
  void rejectsMissingMagnitudeThroughTheConstructor() {
    assertThatNullPointerException()
        .isThrownBy(() -> new MovementQuantity(null))
        .withMessage("Movement quantity is required");
  }

  @ParameterizedTest
  @CsvSource({"1.5, 1.50", "1000, 1E+3", "0.000100, 0.0001"})
  void treatsNumericallyEqualMagnitudesAsTheSameValue(String first, String second) {
    MovementQuantity left = MovementQuantity.of(new BigDecimal(first));
    MovementQuantity right = MovementQuantity.of(new BigDecimal(second));

    assertThat(left).isEqualTo(right);
    assertThat(right).isEqualTo(left);
    assertThat(left.hashCode()).isEqualTo(right.hashCode());
    assertThat(Set.of(left).contains(right)).isTrue();
  }

  @Test
  void supportsEqualValuesAtTheMinimumBigDecimalScale() {
    MovementQuantity first = MovementQuantity.of(new BigDecimal(BigInteger.TEN, Integer.MIN_VALUE));
    MovementQuantity second = MovementQuantity.of(
        new BigDecimal(BigInteger.valueOf(100), Integer.MIN_VALUE + 1));

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
    assertThat(Set.of(first).contains(second)).isTrue();
  }

  @Test
  void distinguishesDifferentMagnitudes() {
    MovementQuantity first = MovementQuantity.of(new BigDecimal("1.5"));
    MovementQuantity second = MovementQuantity.of(new BigDecimal("1.6"));

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void equalsItselfButNotNullOrOtherTypes() {
    MovementQuantity quantity = MovementQuantity.of(new BigDecimal("1.5"));

    assertThat(quantity)
        .isEqualTo(quantity)
        .isNotEqualTo(null)
        .isNotEqualTo(new BigDecimal("1.5"));
  }
}
