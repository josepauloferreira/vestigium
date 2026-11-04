package io.github.josepauloferreira.vestigium.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.josepauloferreira.vestigium.PostgresTestConfiguration;
import io.github.josepauloferreira.vestigium.VestigiumApplication;
import io.github.josepauloferreira.vestigium.application.DuplicateStockEntryIdException;
import io.github.josepauloferreira.vestigium.application.InvalidStockEntryDataException;
import io.github.josepauloferreira.vestigium.application.ItemRepository;
import io.github.josepauloferreira.vestigium.application.StockEntryRepository;
import io.github.josepauloferreira.vestigium.domain.Item;
import io.github.josepauloferreira.vestigium.domain.ItemId;
import io.github.josepauloferreira.vestigium.domain.MovementQuantity;
import io.github.josepauloferreira.vestigium.domain.PhysicalUnit;
import io.github.josepauloferreira.vestigium.domain.StockEntry;
import io.github.josepauloferreira.vestigium.domain.StockEntryId;
import io.github.josepauloferreira.vestigium.domain.StockEntryLine;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@Import(PostgresTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JpaStockEntryRepositoryTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-11-02T13:15:00.123456Z");

  @Autowired private StockEntryRepository entries;
  @Autowired private ItemRepository items;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PostgreSQLContainer postgres;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManagerFactory entityManagerFactory;

  @Test
  void preservesTheCompleteEntryAndItsLineOrder() {
    StockEntry entry = entry(line("1.50"), line("2"), line("0.7500"));

    entries.insert(entry);

    assertAttributes(entries.findById(entry.id()).orElseThrow(), entry);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "1.50",
        "0.000000001",
        "12345678901234567890.12345678901234567890",
        "1E+3",
        "1E-20",
        "1E-16383",
        "1E+131071"
      })
  void preservesDecimalMagnitudeAndOriginalScale(String quantity) {
    StockEntry entry = entry(line(quantity));

    entries.insert(entry);

    assertAttributes(entries.findById(entry.id()).orElseThrow(), entry);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "1969-12-31T23:59:59.999999Z",
        "2026-01-01T00:00:00Z",
        "2099-12-31T23:59:59.000001Z"
      })
  void preservesAcceptedOccurrenceTimesExactly(String occurredAt) {
    StockEntry entry =
        new StockEntry(
            new StockEntryId(UUID.randomUUID()), Instant.parse(occurredAt), List.of(line("1")));

    entries.insert(entry);

    assertAttributes(entries.findById(entry.id()).orElseThrow(), entry);
  }

  @Test
  void returnsAbsenceForAnUnknownIdentity() {
    assertThat(entries.findById(new StockEntryId(UUID.randomUUID()))).isEmpty();
  }

  @Test
  void rejectsAnExistingIdentityWithoutChangingItsOriginalFacts() {
    StockEntry original = entry(line("1.50"));
    StockEntry replacement =
        new StockEntry(original.id(), OCCURRED_AT.plusSeconds(60), List.of(line("9")));
    entries.insert(original);

    assertThatThrownBy(() -> entries.insert(replacement))
        .isInstanceOf(DuplicateStockEntryIdException.class);

    assertAttributes(entries.findById(original.id()).orElseThrow(), original);
  }

  @Test
  void rejectsAnIdenticalResubmission() {
    StockEntry original = entry(line("1.50"));
    entries.insert(original);

    assertThatThrownBy(() -> entries.insert(original))
        .isInstanceOf(DuplicateStockEntryIdException.class);

    assertAttributes(entries.findById(original.id()).orElseThrow(), original);
  }

  @Test
  void rejectsIdentityAlreadyLoadedInAnOuterTransaction() {
    StockEntry original = entry(line("1.50"));
    entries.insert(original);

    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        status -> {
                          assertAttributes(entries.findById(original.id()).orElseThrow(), original);
                          entries.insert(original);
                        }))
        .isInstanceOf(DuplicateStockEntryIdException.class);

    assertAttributes(entries.findById(original.id()).orElseThrow(), original);
  }

  @Test
  void allowsTheSameItemInSeparateEntries() {
    StockEntryLine line = line("1.50");
    StockEntry first = entry(line);
    StockEntry second = entry(line);

    entries.insert(first);
    entries.insert(second);

    assertAttributes(entries.findById(first.id()).orElseThrow(), first);
    assertAttributes(entries.findById(second.id()).orElseThrow(), second);
  }

  @Test
  void rejectsAnUnknownItemWithoutLeavingPartialData() {
    StockEntryLine unknown =
        new StockEntryLine(new ItemId(UUID.randomUUID()), new MovementQuantity(BigDecimal.ONE));
    StockEntry entry = entry(line("1.50"), unknown);

    assertThatThrownBy(() -> entries.insert(entry))
        .isInstanceOf(InvalidStockEntryDataException.class)
        .rootCause()
        .isInstanceOfSatisfying(
            SQLException.class, cause -> assertThat(cause.getSQLState()).isEqualTo("23503"));

    assertNoStoredEntry(entry.id());
  }

  @Test
  void rollsBackTheHeaderAndFirstLineAfterALaterLineFails() {
    StockEntry entry = entry(line("1.50"), line("2"));
    jdbc.execute("CREATE SEQUENCE stock_entry_rollback_probe");
    jdbc.execute(
        """
        CREATE FUNCTION fail_later_stock_entry_line() RETURNS trigger AS $$
        BEGIN
          IF NEW.entry_id = '%s'::UUID AND NEW.line_number = 1 THEN
            IF EXISTS (SELECT 1 FROM stock_entries WHERE id = NEW.entry_id)
               AND EXISTS (SELECT 1 FROM stock_entry_lines WHERE entry_id = NEW.entry_id AND line_number = 0) THEN
              PERFORM nextval('stock_entry_rollback_probe');
            END IF;
            RAISE EXCEPTION 'Synthetic later-line failure'
              USING ERRCODE = '23514', CONSTRAINT = 'synthetic_later_line_failure';
          END IF;
          RETURN NEW;
        END;
        $$ LANGUAGE plpgsql
        """
            .formatted(entry.id().value()));
    jdbc.execute(
        "CREATE TRIGGER fail_later_stock_entry_line BEFORE INSERT ON stock_entry_lines FOR EACH ROW EXECUTE FUNCTION fail_later_stock_entry_line()");
    try {
      assertThatThrownBy(() -> entries.insert(entry))
          .isInstanceOf(DataIntegrityViolationException.class)
          .rootCause()
          .isInstanceOfSatisfying(
              SQLException.class, cause -> assertThat(cause.getSQLState()).isEqualTo("23514"));
      // Sequences survive rollback: this proves the failing write saw both earlier rows.
      assertThat(
              jdbc.queryForObject(
                  "SELECT is_called FROM stock_entry_rollback_probe", Boolean.class))
          .isTrue();
      assertNoStoredEntry(entry.id());
    } finally {
      jdbc.execute("DROP TRIGGER fail_later_stock_entry_line ON stock_entry_lines");
      jdbc.execute("DROP FUNCTION fail_later_stock_entry_line()");
      jdbc.execute("DROP SEQUENCE stock_entry_rollback_probe");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "NaN", "Infinity", "-Infinity"})
  void databaseRejectsNonpositiveOrNonfiniteQuantities(String quantity) {
    StockEntry entry = entry(line("1"));
    entries.insert(entry);
    ItemId another = line("2").itemId();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO stock_entry_lines (entry_id, line_number, item_id, quantity, quantity_scale) VALUES (?, 1, ?, ?::NUMERIC, 0)",
                    entry.id().value(),
                    another.value(),
                    quantity))
        .isInstanceOf(DataIntegrityViolationException.class)
        .rootCause()
        .isInstanceOfSatisfying(
            SQLException.class, cause -> assertThat(cause.getSQLState()).isEqualTo("23514"));

    assertAttributes(entries.findById(entry.id()).orElseThrow(), entry);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void databaseRejectsRepeatedItemsOrPositions(boolean repeatItem) {
    StockEntryLine first = line("1");
    StockEntry entry = entry(first);
    entries.insert(entry);
    ItemId other = line("2").itemId();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO stock_entry_lines (entry_id, line_number, item_id, quantity, quantity_scale) VALUES (?, ?, ?, 1, 0)",
                    entry.id().value(),
                    repeatItem ? 1 : 0,
                    repeatItem ? first.itemId().value() : other.value()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .rootCause()
        .isInstanceOfSatisfying(
            SQLException.class, cause -> assertThat(cause.getSQLState()).isEqualTo("23505"));
    assertAttributes(entries.findById(entry.id()).orElseThrow(), entry);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1E+131072", "1E-16384", "1.1E-16383"})
  void rejectsAnUnsupportedNumericRangeWithoutRounding(String quantity) {
    StockEntry entry = entry(line(quantity));

    assertThatThrownBy(() -> entries.insert(entry))
        .isInstanceOf(InvalidStockEntryDataException.class)
        .hasMessage("Stock entry quantity exceeds PostgreSQL NUMERIC storage limits");
    assertNoStoredEntry(entry.id());
  }

  @Test
  void preservesTrailingZeroScaleBeyondTheDatabaseFractionalLimit() {
    ItemId item = line("1").itemId();
    StockEntry entry =
        entry(new StockEntryLine(item, new MovementQuantity(BigDecimal.ONE.setScale(20_000))));

    entries.insert(entry);

    assertAttributes(entries.findById(entry.id()).orElseThrow(), entry);
  }

  @Test
  void rejectsAnOccurrenceOutsideTheDatabaseRangeInsteadOfStoringInfinity() {
    StockEntry entry =
        new StockEntry(
            new StockEntryId(UUID.randomUUID()),
            Instant.parse("+300000-01-01T00:00:00Z"),
            List.of(line("1")));

    assertThatThrownBy(() -> entries.insert(entry))
        .isInstanceOf(InvalidStockEntryDataException.class);
    assertNoStoredEntry(entry.id());
  }

  @Test
  void retainsCommittedFactsAcrossClosedApplicationContexts() {
    StockEntry entry = entry(line("1.50"), line("0.7500"));
    try (ConfigurableApplicationContext first = startApplication()) {
      first.getBean(StockEntryRepository.class).insert(entry);
    }
    try (ConfigurableApplicationContext second = startApplication()) {
      assertAttributes(
          second.getBean(StockEntryRepository.class).findById(entry.id()).orElseThrow(), entry);
    }
  }

  @Test
  void upgradesAV1CatalogWithoutChangingItsExistingItems() {
    String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
    Flyway v1 =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas(schema)
            .defaultSchema(schema)
            .target("1")
            .load();
    try {
      assertThat(v1.migrate().migrationsExecuted).isEqualTo(1);
      UUID id = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO " + schema + ".items (id, name, unit) VALUES (?, ?, ?)",
          id,
          "Synthetic upgrade fixture",
          "LITRE");
      Flyway latest =
          Flyway.configure()
              .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
              .schemas(schema)
              .defaultSchema(schema)
              .load();
      assertThat(latest.migrate().migrationsExecuted).isEqualTo(2);
      assertThat(latest.info().applied())
          .filteredOn(info -> info.getVersion() != null)
          .extracting(info -> info.getVersion().toString())
          .containsExactly("1", "2", "3");
      assertThat(latest.migrate().migrationsExecuted).isZero();
      assertThat(
              jdbc.queryForMap("SELECT id, name, unit FROM " + schema + ".items WHERE id = ?", id))
          .containsEntry("id", id)
          .containsEntry("name", "Synthetic upgrade fixture")
          .containsEntry("unit", "LITRE");
    } finally {
      jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }
  }

  private StockEntryLine line(String quantity) {
    Item item = new Item(new ItemId(UUID.randomUUID()), "Synthetic material", PhysicalUnit.LITRE);
    items.insert(item);
    return new StockEntryLine(item.id(), new MovementQuantity(new BigDecimal(quantity)));
  }

  @Test
  void queriesOnlyMatchingReceiptsAndPreservesTheirCompleteLines() {
    StockEntryLine wanted = line("1.50");
    StockEntryLine other = line("0.7500");
    StockEntry first = entry(other, wanted);
    StockEntry second =
        entry(new StockEntryLine(wanted.itemId(), MovementQuantity.of(new BigDecimal("2.00"))));
    entries.insert(first);
    entries.insert(second);
    entries.insert(entry(line("99")));

    List<StockEntry> found = entries.findByItemId(wanted.itemId());

    assertThat(found).hasSize(2);
    assertAttributes(
        found.stream().filter(e -> e.id().equals(first.id())).findFirst().orElseThrow(), first);
    assertAttributes(
        found.stream().filter(e -> e.id().equals(second.id())).findFirst().orElseThrow(), second);
  }

  @Test
  void ordersMatchingReceiptsByTimeThenDatabaseUuidOrder() {
    StockEntryLine wanted = line("1");
    StockEntry low =
        new StockEntry(
            new StockEntryId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
            OCCURRED_AT,
            List.of(wanted));
    StockEntry high =
        new StockEntry(
            new StockEntryId(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")),
            OCCURRED_AT,
            List.of(wanted));
    StockEntry earlier =
        new StockEntry(
            new StockEntryId(UUID.randomUUID()), OCCURRED_AT.minusSeconds(1), List.of(wanted));
    entries.insert(high);
    entries.insert(earlier);
    entries.insert(low);

    assertThat(entries.findByItemId(wanted.itemId()))
        .extracting(StockEntry::id)
        .containsExactly(earlier.id(), low.id(), high.id());
  }

  @Test
  void returnsAnEmptyHistoryWhenNoReceiptMatches() {
    assertThat(entries.findByItemId(line("1").itemId())).isEmpty();
    assertThat(entries.findByItemId(new ItemId(UUID.randomUUID()))).isEmpty();
  }

  @Test
  void loadsCompleteHistoriesWithoutPerReceiptCollectionQueries() {
    StockEntryLine wanted = line("1.50");
    entries.insert(entry(wanted, line("2")));
    entries.insert(entry(line("3"), wanted));
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    boolean previous = statistics.isStatisticsEnabled();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
    try {
      List<StockEntry> found = entries.findByItemId(wanted.itemId());
      assertThat(found).hasSize(2).allSatisfy(e -> assertThat(e.lines()).hasSize(2));
      assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    } finally {
      statistics.setStatisticsEnabled(previous);
    }
  }

  @Test
  void upgradesV2WithExistingReceiptDataAndAddsTheItemLookupIndex() {
    String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
    Flyway v2 =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas(schema)
            .defaultSchema(schema)
            .target("2")
            .load();
    try {
      assertThat(v2.migrate().migrationsExecuted).isEqualTo(2);
      UUID item = UUID.randomUUID();
      UUID entry = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO " + schema + ".items VALUES (?, 'Synthetic upgrade item', 'LITRE')", item);
      jdbc.update(
          "INSERT INTO " + schema + ".stock_entries VALUES (?, ?)",
          entry,
          java.sql.Timestamp.from(OCCURRED_AT));
      jdbc.update(
          "INSERT INTO " + schema + ".stock_entry_lines VALUES (?, 0, ?, 1.50, 2)", entry, item);
      Flyway v3 =
          Flyway.configure()
              .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
              .schemas(schema)
              .defaultSchema(schema)
              .load();
      assertThat(v3.migrate().migrationsExecuted).isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT quantity FROM " + schema + ".stock_entry_lines WHERE entry_id = ?",
                  BigDecimal.class,
                  entry))
          .isEqualTo(new BigDecimal("1.50"));
      assertThat(
              jdbc.queryForObject(
                  "SELECT quantity_scale FROM " + schema + ".stock_entry_lines WHERE entry_id = ?",
                  Integer.class,
                  entry))
          .isEqualTo(2);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM pg_indexes WHERE schemaname = ? AND indexname = 'stock_entry_lines_item_id_idx'",
                  Long.class,
                  schema))
          .isEqualTo(1L);
    } finally {
      jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }
  }

  private static StockEntry entry(StockEntryLine... lines) {
    return new StockEntry(new StockEntryId(UUID.randomUUID()), OCCURRED_AT, List.of(lines));
  }

  private void assertNoStoredEntry(StockEntryId id) {
    assertThat(entries.findById(id)).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM stock_entries WHERE id = ?", Long.class, id.value()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM stock_entry_lines WHERE entry_id = ?",
                Long.class,
                id.value()))
        .isZero();
  }

  private static void assertAttributes(StockEntry actual, StockEntry expected) {
    assertThat(actual.id()).isEqualTo(expected.id());
    assertThat(actual.occurredAt()).isEqualTo(expected.occurredAt());
    assertThat(actual.lines()).hasSize(expected.lines().size());
    for (int index = 0; index < expected.lines().size(); index++) {
      StockEntryLine wanted = expected.lines().get(index);
      StockEntryLine stored = actual.lines().get(index);
      assertThat(stored.itemId()).isEqualTo(wanted.itemId());
      assertThat(stored.quantity().value()).isEqualTo(wanted.quantity().value());
    }
  }

  private ConfigurableApplicationContext startApplication() {
    return new SpringApplicationBuilder(VestigiumApplication.class)
        .web(WebApplicationType.NONE)
        .bannerMode(Banner.Mode.OFF)
        .logStartupInfo(false)
        .run(
            "--spring.datasource.url=" + postgres.getJdbcUrl(),
            "--spring.datasource.username=" + postgres.getUsername(),
            "--spring.datasource.password=" + postgres.getPassword());
  }
}
