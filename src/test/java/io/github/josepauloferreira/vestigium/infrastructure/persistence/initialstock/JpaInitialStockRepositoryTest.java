package io.github.josepauloferreira.vestigium.infrastructure.persistence.initialstock;

import static org.assertj.core.api.Assertions.*;

import io.github.josepauloferreira.vestigium.PostgresTestConfiguration;
import io.github.josepauloferreira.vestigium.application.initialstock.DuplicateInitialStockException;
import io.github.josepauloferreira.vestigium.application.initialstock.InitialStockRepository;
import io.github.josepauloferreira.vestigium.application.initialstock.InvalidInitialStockDataException;
import io.github.josepauloferreira.vestigium.application.item.ItemNotFoundException;
import io.github.josepauloferreira.vestigium.application.item.ItemRepository;
import io.github.josepauloferreira.vestigium.domain.initialstock.InitialStock;
import io.github.josepauloferreira.vestigium.domain.initialstock.InitialStockQuantity;
import io.github.josepauloferreira.vestigium.domain.item.Item;
import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import io.github.josepauloferreira.vestigium.domain.item.PhysicalUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(PostgresTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JpaInitialStockRepositoryTest {
  @Autowired InitialStockRepository stocks;
  @Autowired ItemRepository items;
  @Autowired JdbcTemplate jdbc;
  @Autowired PostgreSQLContainer postgres;
  private final Instant start = Instant.parse("2026-01-01T08:00:00.123456Z");

  @ParameterizedTest
  @ValueSource(strings = {"0", "0.0000", "50.00", "0.00001", "1E+3", "1E-16383", "1E+131071"})
  void preservesExactQuantityAndTime(String number) {
    ItemId id = item();
    var stock = new InitialStock(id, InitialStockQuantity.of(new BigDecimal(number)), start);
    stocks.insert(stock);
    var stored = stocks.findByItemId(id).orElseThrow();
    assertThat(stored.itemId()).isEqualTo(id);
    assertThat(stored.quantity().value()).isEqualTo(new BigDecimal(number));
    assertThat(stored.occurredAt()).isEqualTo(start);
  }

  @Test
  void distinguishesAbsenceAndRecordedZeroAndPreservesDuplicateOriginal() {
    ItemId id = item();
    assertThat(stocks.findByItemId(id)).isEmpty();
    stocks.insert(new InitialStock(id, InitialStockQuantity.of(BigDecimal.ZERO), start));
    assertThatThrownBy(
            () ->
                stocks.insert(new InitialStock(id, InitialStockQuantity.of(BigDecimal.TEN), start)))
        .isInstanceOf(DuplicateInitialStockException.class);
    assertThat(stocks.findByItemId(id).orElseThrow().quantity().value()).isZero();
  }

  @Test
  void rejectsUnknownCatalogReferencesAndUnsupportedStorage() {
    assertThatThrownBy(
            () ->
                stocks.insert(
                    new InitialStock(
                        new ItemId(UUID.randomUUID()),
                        InitialStockQuantity.of(BigDecimal.ZERO),
                        start)))
        .isInstanceOf(ItemNotFoundException.class);
    ItemId id = item();
    assertThatThrownBy(
            () ->
                stocks.insert(
                    new InitialStock(
                        id, InitialStockQuantity.of(new BigDecimal("1E-16384")), start)))
        .isInstanceOf(InvalidInitialStockDataException.class);
    assertThatThrownBy(
            () ->
                stocks.insert(
                    new InitialStock(
                        id, InitialStockQuantity.of(BigDecimal.ONE), Instant.MAX.minusNanos(999))))
        .isInstanceOf(InvalidInitialStockDataException.class);
    assertThat(stocks.findByItemId(id)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "'Infinity'", "'NaN'", "NULL"})
  void databaseRejectsInvalidQuantities(String literal) {
    ItemId id = item();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO initial_stock VALUES (?, " + literal + ", 0, ?)",
                    id.value(),
                    java.sql.Timestamp.from(start)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void preservesUnexpectedIntegrityFailuresAndRollsBackTheOpening() {
    ItemId id = item();
    jdbc.execute(
        "ALTER TABLE initial_stock ADD CONSTRAINT unexpected_opening_probe CHECK (quantity <> 98765.4321)");
    try {
      var opening =
          new InitialStock(id, InitialStockQuantity.of(new BigDecimal("98765.4321")), start);

      assertThatThrownBy(() -> stocks.insert(opening))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(stocks.findByItemId(id)).isEmpty();
    } finally {
      jdbc.execute("ALTER TABLE initial_stock DROP CONSTRAINT unexpected_opening_probe");
    }
  }

  @Test
  void upgradesV3WithoutChangingExistingReceipts() {
    String schema = "opening_" + UUID.randomUUID().toString().replace("-", "");
    Flyway old =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas(schema)
            .defaultSchema(schema)
            .target("3")
            .load();
    try {
      assertThat(old.migrate().migrationsExecuted).isEqualTo(3);
      UUID id = UUID.randomUUID(), entry = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO " + schema + ".items VALUES (?, 'Synthetic upgrade material','LITRE')", id);
      jdbc.update(
          "INSERT INTO " + schema + ".stock_entries VALUES (?,?)",
          entry,
          java.sql.Timestamp.from(start));
      jdbc.update("INSERT INTO " + schema + ".stock_entry_lines VALUES (?,0,?,10.50,2)", entry, id);
      Flyway latest =
          Flyway.configure()
              .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
              .schemas(schema)
              .defaultSchema(schema)
              .load();
      assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT quantity FROM " + schema + ".stock_entry_lines WHERE entry_id=?",
                  BigDecimal.class,
                  entry))
          .isEqualTo(new BigDecimal("10.50"));
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM " + schema + ".initial_stock", Integer.class))
          .isZero();
      assertThat(latest.migrate().migrationsExecuted).isZero();
    } finally {
      jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }
  }

  private ItemId item() {
    ItemId id = new ItemId(UUID.randomUUID());
    items.insert(new Item(id, "Synthetic opening material", PhysicalUnit.LITRE));
    return id;
  }
}
