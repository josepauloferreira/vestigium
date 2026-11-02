package io.github.josepauloferreira.vestigium.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.josepauloferreira.vestigium.PostgresTestConfiguration;
import io.github.josepauloferreira.vestigium.VestigiumApplication;
import io.github.josepauloferreira.vestigium.application.DuplicateItemIdException;
import io.github.josepauloferreira.vestigium.application.ItemRepository;
import io.github.josepauloferreira.vestigium.domain.Item;
import io.github.josepauloferreira.vestigium.domain.ItemId;
import io.github.josepauloferreira.vestigium.domain.PhysicalUnit;
import java.sql.SQLException;
import java.util.UUID;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
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
class JpaItemRepositoryTest {

  @Autowired private ItemRepository repository;
  @Autowired private Flyway flyway;
  @Autowired private PostgreSQLContainer postgres;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;

  @ParameterizedTest
  @EnumSource(PhysicalUnit.class)
  void preservesEachPhysicalUnit(PhysicalUnit unit) {
    Item item = item("Synthetic material", unit);

    repository.insert(item);

    assertAttributes(repository.findById(item.id()).orElseThrow(), item);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "  Synthetic OIL  ",
        "D'água; DROP TABLE items; --",
        "Óleo hidráulico 🛠",
        "\tSynthetic\nitem\t"
      })
  void preservesNamesWithoutNormalization(String name) {
    Item item = item(name, PhysicalUnit.LITRE);

    repository.insert(item);

    assertAttributes(repository.findById(item.id()).orElseThrow(), item);
  }

  @Test
  void preservesNamesLongerThanTheDefaultVarcharLength() {
    Item item = item("Synthetic material ".repeat(100), PhysicalUnit.LITRE);

    repository.insert(item);

    assertAttributes(repository.findById(item.id()).orElseThrow(), item);
  }

  @ParameterizedTest
  @MethodSource("invalidDatabaseAttributes")
  void enforcesMigratedConstraintsWithoutJpa(String name, String unit, String sqlState) {
    ItemId id = new ItemId(UUID.randomUUID());

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO items (id, name, unit) VALUES (?, ?, ?)", id.value(), name, unit))
        .isInstanceOf(DataIntegrityViolationException.class)
        .rootCause()
        .isInstanceOfSatisfying(
            SQLException.class, cause -> assertThat(cause.getSQLState()).isEqualTo(sqlState));
    assertThat(repository.findById(id)).isEmpty();
  }

  private static Stream<Arguments> invalidDatabaseAttributes() {
    return Stream.of(
        Arguments.of(null, "KG", "23502"),
        Arguments.of("Synthetic material", null, "23502"),
        Arguments.of("Synthetic material", "UNSUPPORTED", "23514"));
  }

  @Test
  void returnsAbsenceForUnknownIdentity() {
    assertThat(repository.findById(new ItemId(UUID.randomUUID()))).isEmpty();
  }

  @Test
  void rejectsRepeatedIdentityWithoutOverwritingTheOriginal() {
    Item original = item("Original material", PhysicalUnit.KG);
    Item replacement = new Item(original.id(), "Different material", PhysicalUnit.ROLL);
    repository.insert(original);

    assertThatThrownBy(() -> repository.insert(replacement))
        .isInstanceOf(DuplicateItemIdException.class);
    assertAttributes(repository.findById(original.id()).orElseThrow(), original);
  }

  @Test
  void rejectsRepeatedIdentityEvenWithIdenticalAttributes() {
    Item original = item("Synthetic material", PhysicalUnit.KG);
    repository.insert(original);

    assertThatThrownBy(() -> repository.insert(original))
        .isInstanceOf(DuplicateItemIdException.class);
    assertAttributes(repository.findById(original.id()).orElseThrow(), original);
  }

  @Test
  void rejectsIdentityAlreadyLoadedInTheCurrentTransaction() {
    Item original = item("Original material", PhysicalUnit.KG);
    Item replacement = new Item(original.id(), "Different material", PhysicalUnit.ROLL);
    repository.insert(original);

    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        status -> {
                          assertAttributes(
                              repository.findById(original.id()).orElseThrow(), original);
                          repository.insert(replacement);
                        }))
        .isInstanceOf(DuplicateItemIdException.class);
    assertAttributes(repository.findById(original.id()).orElseThrow(), original);
  }

  @Test
  void preservesOtherDataFailuresInsteadOfReportingAnIdentityConflict() {
    Item item = item("Synthetic" + Character.MIN_VALUE + "material", PhysicalUnit.KG);

    assertThatThrownBy(() -> repository.insert(item))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(repository.findById(item.id())).isEmpty();
  }

  @Test
  void allowsSeparateIdentitiesWithTheSameName() {
    Item first = item("Synthetic material", PhysicalUnit.UNIT);
    Item second = item("Synthetic material", PhysicalUnit.PAIR);

    repository.insert(first);
    repository.insert(second);

    assertAttributes(repository.findById(first.id()).orElseThrow(), first);
    assertAttributes(repository.findById(second.id()).orElseThrow(), second);
  }

  @Test
  void appliesTheVersionedSchemaWithoutReapplyingIt() {
    assertThat(flyway.info().applied()).hasSize(1);
    assertThat(flyway.migrate().migrationsExecuted).isZero();
  }

  @Test
  void retainsCommittedItemAfterClosingAndReopeningTheApplication() {
    Item item = item("Persisted across application contexts", PhysicalUnit.LITRE);

    try (ConfigurableApplicationContext first = startApplication()) {
      first.getBean(ItemRepository.class).insert(item);
    }

    try (ConfigurableApplicationContext second = startApplication()) {
      Item stored = second.getBean(ItemRepository.class).findById(item.id()).orElseThrow();
      assertAttributes(stored, item);
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

  private static Item item(String name, PhysicalUnit unit) {
    return new Item(new ItemId(UUID.randomUUID()), name, unit);
  }

  private static void assertAttributes(Item actual, Item expected) {
    assertThat(actual)
        .extracting(Item::id, Item::name, Item::unit)
        .containsExactly(expected.id(), expected.name(), expected.unit());
  }
}
