package io.github.josepauloferreira.vestigium.domain.initialstock;

import static org.assertj.core.api.Assertions.*;

import io.github.josepauloferreira.vestigium.domain.balance.StockBalanceCalculator;
import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import io.github.josepauloferreira.vestigium.domain.stock.MovementQuantity;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntry;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntryId;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntryLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InitialStockTest {
  private final ItemId item = new ItemId(UUID.randomUUID());
  private final Instant start = Instant.parse("2026-01-01T08:00:00.123456Z");

  @ParameterizedTest
  @ValueSource(strings = {"0", "0.00", "50.00", "0.00001", "1E+3"})
  void preservesNonnegativeExactQuantities(String number) {
    var quantity = new InitialStockQuantity(new BigDecimal(number));
    assertThat(quantity.value()).isEqualTo(new BigDecimal(number));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "50", "0.00001"})
  void equalityAndHashUseNumericMeaning(String number) {
    var a = InitialStockQuantity.of(new BigDecimal(number));
    var b = new InitialStockQuantity(new BigDecimal(number).setScale(10));
    assertThat(a).isEqualTo(b);
    assertThat(Set.of(a)).contains(b);
    assertThat(a).isNotEqualTo(new InitialStockQuantity(new BigDecimal("999")));
  }

  @Test
  void rejectsMissingAndNegativeValues() {
    assertThatNullPointerException().isThrownBy(() -> new InitialStockQuantity(null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new InitialStockQuantity(new BigDecimal("-0.01")));
  }

  @Test
  void requiresAllFactFieldsAndMicrosecondPrecision() {
    var quantity = new InitialStockQuantity(BigDecimal.ZERO);
    assertThatNullPointerException().isThrownBy(() -> new InitialStock(null, quantity, start));
    assertThatNullPointerException().isThrownBy(() -> new InitialStock(item, null, start));
    assertThatNullPointerException().isThrownBy(() -> new InitialStock(item, quantity, null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new InitialStock(item, quantity, start.plusNanos(1)));
    var stock = new InitialStock(item, quantity, start);
    assertThat(stock.precedesOrEquals(start)).isTrue();
    assertThat(stock.precedesOrEquals(start.plusSeconds(1))).isTrue();
    assertThat(stock.precedesOrEquals(start.minusSeconds(1))).isFalse();
    assertThat(stock)
        .isEqualTo(new InitialStock(item, new InitialStockQuantity(BigDecimal.TEN), start));
    assertThat(stock.hashCode()).isEqualTo(item.hashCode());
  }

  @Test
  void calculatesOpeningPlusReceiptsWithoutChangingScale() {
    var stock = new InitialStock(item, new InitialStockQuantity(new BigDecimal("50.00")), start);
    var entry = receipt(item, start, "10.50");
    var calculator = new StockBalanceCalculator();
    assertThat(calculator.calculate(item, stock, List.of(entry)))
        .isEqualTo(new BigDecimal("60.50"));
    assertThat(calculator.calculate(item, stock, List.of())).isEqualTo(new BigDecimal("50.00"));
    assertThat(stock.quantity().value()).isEqualTo(new BigDecimal("50.00"));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                calculator.calculate(
                    item, stock, List.of(receipt(item, start.minusSeconds(1), "1"))));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> calculator.calculate(new ItemId(UUID.randomUUID()), stock, List.of()));
  }

  @Test
  void zeroDoesNotAlignAnExtremeScaleAndUnrelatedReceiptsDoNotSetChronology() {
    var stock = new InitialStock(item, new InitialStockQuantity(BigDecimal.ZERO), start);
    var extreme = new BigDecimal(java.math.BigInteger.ONE, Integer.MIN_VALUE);
    var entry =
        new StockEntry(
            new StockEntryId(UUID.randomUUID()),
            start,
            List.of(new StockEntryLine(item, new MovementQuantity(extreme))));
    assertThat(new StockBalanceCalculator().calculate(item, stock, List.of(entry)))
        .isEqualTo(extreme);
    assertThat(
            new StockBalanceCalculator()
                .calculate(
                    item,
                    stock,
                    List.of(receipt(new ItemId(UUID.randomUUID()), start.minusSeconds(1), "1"))))
        .isZero();
  }

  private StockEntry receipt(ItemId id, Instant time, String quantity) {
    return new StockEntry(
        new StockEntryId(UUID.randomUUID()),
        time,
        List.of(new StockEntryLine(id, new MovementQuantity(new BigDecimal(quantity)))));
  }
}
