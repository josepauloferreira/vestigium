package io.github.josepauloferreira.vestigium.infrastructure.web.stockentry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.josepauloferreira.vestigium.PostgresTestConfiguration;
import io.github.josepauloferreira.vestigium.VestigiumApplication;
import io.github.josepauloferreira.vestigium.domain.item.PhysicalUnit;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StockEntryHttpTest {

  @LocalServerPort private int port;
  @Autowired private JsonMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PostgreSQLContainer postgres;

  @Test
  void registersAndQueriesCompleteOrderedEntries() throws Exception {
    UUID id = UUID.randomUUID();
    UUID first = registerItem(PhysicalUnit.LITRE);
    UUID second = registerItem(PhysicalUnit.PAIR);
    String occurredAt = "2026-11-03T14:30:00.123456Z";
    String body = entryJson(id, occurredAt, line(first, "1.50"), line(second, "4"));

    HttpResponse<String> created = send(port, "/stock-entries", body);

    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(created.headers().firstValue("Location")).hasValue("/stock-entries/" + id);
    StockEntryResponse expected =
        new StockEntryResponse(
            id,
            occurredAt,
            List.of(
                new StockEntryResponse.Line(first, new BigDecimal("1.50")),
                new StockEntryResponse.Line(second, new BigDecimal("4"))));
    assertRepresentation(created, expected);
    assertEntry(send(port, "/stock-entries/" + id, null), expected);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM stock_entry_lines WHERE entry_id = ?", Long.class, id))
        .isEqualTo(2L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1.50", "0.7500", "1E+3", "1234567890.1234567890123456789", "1E-16383"})
  void preservesExactDecimalValueAndScale(String quantity) throws Exception {
    UUID id = UUID.randomUUID();
    UUID item = registerItem(PhysicalUnit.UNIT);
    String occurredAt = "2026-11-03T14:30:00Z";

    assertThat(
            send(port, "/stock-entries", entryJson(id, occurredAt, line(item, quantity)))
                .statusCode())
        .isEqualTo(201);

    assertEntry(
        send(port, "/stock-entries/" + id, null),
        new StockEntryResponse(
            id, occurredAt, List.of(new StockEntryResponse.Line(item, new BigDecimal(quantity)))));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2026-11-03T11:30:00.123456-03:00",
        "1969-12-31T23:59:59.999999Z",
        "2100-01-01T00:00:00Z",
        "2026-11-03T14:30:00.123456000Z"
      })
  void preservesTheExplicitInstantWithoutInventingChronologyRules(String occurredAt)
      throws Exception {
    UUID id = UUID.randomUUID();
    UUID item = registerItem(PhysicalUnit.KG);

    assertThat(
            send(port, "/stock-entries", entryJson(id, occurredAt, line(item, "1"))).statusCode())
        .isEqualTo(201);

    assertEntry(
        send(port, "/stock-entries/" + id, null),
        new StockEntryResponse(
            id,
            OffsetDateTime.parse(occurredAt).toInstant().toString(),
            List.of(new StockEntryResponse.Line(item, BigDecimal.ONE))));
  }

  @ParameterizedTest
  @MethodSource("invalidBodies")
  void rejectsInvalidInputWithoutWriting(String template) throws Exception {
    UUID id = UUID.randomUUID();
    UUID item = registerItem(PhysicalUnit.ROLL);
    Long before = jdbc.queryForObject("SELECT count(*) FROM stock_entries", Long.class);
    Long beforeLines = jdbc.queryForObject("SELECT count(*) FROM stock_entry_lines", Long.class);
    String body = template.replace("$entry", id.toString()).replace("$item", item.toString());

    assertProblem(send(port, "/stock-entries", body), 400, "/stock-entries");

    assertNoStoredEntry(id);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_entries", Long.class))
        .isEqualTo(before);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_entry_lines", Long.class))
        .isEqualTo(beforeLines);
  }

  static Stream<String> invalidBodies() {
    String valid =
        "{\"id\":\"$entry\",\"occurredAt\":\"2026-11-03T14:30:00Z\",\"lines\":[{\"itemId\":\"$item\",\"quantity\":1}]}";
    return Stream.of(
        "",
        "{",
        "null",
        "[]",
        "{}",
        valid.replace("\"id\":\"$entry\",", ""),
        valid.replace("\"$entry\"", "null"),
        valid.replace("$entry", "1-1-1-1-1"),
        valid.replace("\"$entry\"", "true"),
        valid.replace("\"occurredAt\":\"2026-11-03T14:30:00Z\",", ""),
        valid.replace("\"2026-11-03T14:30:00Z\"", "null"),
        valid.replace("\"2026-11-03T14:30:00Z\"", "42"),
        valid.replace("2026-11-03T14:30:00Z", "2026-11-03"),
        valid.replace("2026-11-03T14:30:00Z", "2026-11-03T14:30:00"),
        valid.replace("2026-11-03T14:30:00Z", "2026-11-03T14:30:00.1234567Z"),
        valid.replace("2026-11-03T14:30:00Z", "2026-11-03T14:30:00.000000001Z"),
        valid.replace("[{\"itemId\":\"$item\",\"quantity\":1}]", "null"),
        valid.replace("[{\"itemId\":\"$item\",\"quantity\":1}]", "[]"),
        valid.replace("[{\"itemId\":\"$item\",\"quantity\":1}]", "[null]"),
        valid.replace("[{\"itemId\":\"$item\",\"quantity\":1}]", "[{}]"),
        valid.replace("\"itemId\":\"$item\",", ""),
        valid.replace("\"$item\"", "null"),
        valid.replace("$item", "1-1-1-1-1"),
        valid.replace("\"$item\"", "42"),
        valid.replace("\"quantity\":1", "\"quantity\":null"),
        valid.replace("\"quantity\":1", "\"quantity\":0"),
        valid.replace("\"quantity\":1", "\"quantity\":-0.5"),
        valid.replace("\"quantity\":1", "\"quantity\":\"1.50\""),
        valid.replace("\"quantity\":1", "\"quantity\":true"),
        valid.replace("\"quantity\":1", "\"quantity\":[]"),
        valid.replace("\"quantity\":1", "\"quantity\":{}"));
  }

  @Test
  void identifiesNestedFieldErrors() throws Exception {
    UUID id = UUID.randomUUID();
    UUID item = registerItem(PhysicalUnit.KG);
    HttpResponse<String> response =
        send(port, "/stock-entries", entryJson(id, "2026-11-03T14:30:00Z", line(item, "0")));

    assertProblem(response, 400, "/stock-entries");
    assertThat(JsonPath.parse(response.body()).<List<String>>read("$.errors[*].field"))
        .contains("lines[0].quantity");
  }

  @Test
  void rejectsTheSameItemTwiceWithoutCombiningLines() throws Exception {
    UUID id = UUID.randomUUID();
    UUID item = registerItem(PhysicalUnit.LITRE);
    String second =
        "{\"itemId\":\""
            + item.toString().toUpperCase(java.util.Locale.ROOT)
            + "\",\"quantity\":3}";

    assertProblem(
        send(
            port, "/stock-entries", entryJson(id, "2026-11-03T14:30:00Z", line(item, "2"), second)),
        400,
        "/stock-entries");

    assertNoStoredEntry(id);
  }

  @Test
  void rejectsAnUnknownLaterItemAndRollsBackTheWholeEntry() throws Exception {
    UUID id = UUID.randomUUID();
    UUID item = registerItem(PhysicalUnit.LITRE);

    HttpResponse<String> response =
        send(
            port,
            "/stock-entries",
            entryJson(
                id, "2026-11-03T14:30:00Z", line(item, "1.50"), line(UUID.randomUUID(), "2")));

    assertProblem(response, 422, "/stock-entries");
    assertThat(JsonPath.parse(response.body()).<String>read("$.detail"))
        .isEqualTo("Every stock entry item must exist in the catalog");
    assertNoStoredEntry(id);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1E+131072", "1E-16384"})
  void rejectsUnsupportedStorageQuantitiesWithoutRounding(String quantity) throws Exception {
    UUID id = UUID.randomUUID();
    UUID item = registerItem(PhysicalUnit.KG);

    assertProblem(
        send(port, "/stock-entries", entryJson(id, "2026-11-03T14:30:00Z", line(item, quantity))),
        422,
        "/stock-entries");

    assertNoStoredEntry(id);
  }

  @Test
  void rejectsAnOccurrenceOutsideDatabaseStorageRange() throws Exception {
    UUID id = UUID.randomUUID();
    UUID item = registerItem(PhysicalUnit.KG);

    assertProblem(
        send(port, "/stock-entries", entryJson(id, "+300000-01-01T00:00:00Z", line(item, "1"))),
        422,
        "/stock-entries");

    assertNoStoredEntry(id);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void rejectsDuplicateIdentitiesWithoutChangingStoredFacts(boolean identical) throws Exception {
    UUID id = UUID.randomUUID();
    UUID item = registerItem(PhysicalUnit.KG);
    String original = entryJson(id, "2026-11-03T14:30:00Z", line(item, "1.50"));
    assertThat(send(port, "/stock-entries", original).statusCode()).isEqualTo(201);

    assertProblem(
        send(
            port,
            "/stock-entries",
            identical ? original : entryJson(id, "2026-11-04T14:30:00Z", line(item, "9"))),
        409,
        "/stock-entries");

    assertEntry(
        send(port, "/stock-entries/" + id, null),
        new StockEntryResponse(
            id,
            "2026-11-03T14:30:00Z",
            List.of(new StockEntryResponse.Line(item, new BigDecimal("1.50")))));
  }

  @Test
  void acceptsSeparateIdentitiesWithTheSameReceiptContents() throws Exception {
    UUID item = registerItem(PhysicalUnit.KG);
    for (int i = 0; i < 2; i++) {
      UUID id = UUID.randomUUID();
      assertThat(
              send(port, "/stock-entries", entryJson(id, "2026-11-03T14:30:00Z", line(item, "1")))
                  .statusCode())
          .isEqualTo(201);
      assertEntry(
          send(port, "/stock-entries/" + id, null),
          new StockEntryResponse(
              id,
              "2026-11-03T14:30:00Z",
              List.of(new StockEntryResponse.Line(item, BigDecimal.ONE))));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-a-uuid", "1-1-1-1-1"})
  void rejectsInvalidLookupIds(String id) throws Exception {
    assertProblem(send(port, "/stock-entries/" + id, null), 400, "/stock-entries/" + id);
  }

  @Test
  void returnsNotFoundForAnUnknownEntry() throws Exception {
    String path = "/stock-entries/" + UUID.randomUUID();
    assertProblem(send(port, path, null), 404, path);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void keepsUnexpectedPersistenceFailuresAsServerErrors(boolean creating) throws Exception {
    UUID id = UUID.randomUUID();
    UUID item = registerItem(PhysicalUnit.KG);
    String path = creating ? "/stock-entries" : "/stock-entries/" + id;
    String body = creating ? entryJson(id, "2026-11-03T14:30:00Z", line(item, "1")) : null;
    jdbc.execute("ALTER TABLE stock_entries RENAME TO entries_http_failure_probe");
    try {
      HttpResponse<String> response = send(port, path, body);
      assertProblem(response, 500, path);
      assertThat(JsonPath.parse(response.body()).<String>read("$.detail"))
          .isEqualTo("Unable to complete the request");
    } finally {
      jdbc.execute("ALTER TABLE entries_http_failure_probe RENAME TO stock_entries");
    }
  }

  @Test
  void doesNotRelabelAnUnrelatedIntegrityFailureAsClientInput() throws Exception {
    UUID id = UUID.randomUUID();
    UUID item = registerItem(PhysicalUnit.KG);
    jdbc.execute(
        """
        CREATE FUNCTION reject_http_entry_probe() RETURNS trigger AS $$
        BEGIN
          RAISE EXCEPTION 'Synthetic unrelated constraint failure'
            USING ERRCODE = '23514', CONSTRAINT = 'unrelated_http_entry_check';
        END;
        $$ LANGUAGE plpgsql
        """);
    try {
      jdbc.execute(
          "CREATE TRIGGER reject_http_entry_probe BEFORE INSERT ON stock_entries FOR EACH ROW EXECUTE FUNCTION reject_http_entry_probe()");
      HttpResponse<String> response =
          send(port, "/stock-entries", entryJson(id, "2026-11-03T14:30:00Z", line(item, "1")));
      assertProblem(response, 500, "/stock-entries");
      assertThat(response.body())
          .doesNotContain("Synthetic unrelated", "unrelated_http_entry_check");
      assertNoStoredEntry(id);
    } finally {
      jdbc.execute("DROP TRIGGER IF EXISTS reject_http_entry_probe ON stock_entries");
      jdbc.execute("DROP FUNCTION reject_http_entry_probe()");
    }
  }

  @Test
  void retrievesTheCompleteEntryAfterRestartingTheHttpApplication() throws Exception {
    UUID id = UUID.randomUUID();
    UUID item = registerItem(PhysicalUnit.LITRE);
    String occurredAt = "2026-11-03T14:30:00.123456Z";
    try (ConfigurableApplicationContext first = startApplication()) {
      int firstPort =
          first.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
      assertThat(
              send(firstPort, "/stock-entries", entryJson(id, occurredAt, line(item, "1.50")))
                  .statusCode())
          .isEqualTo(201);
    }
    try (ConfigurableApplicationContext second = startApplication()) {
      int secondPort =
          second.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
      assertEntry(
          send(secondPort, "/stock-entries/" + id, null),
          new StockEntryResponse(
              id, occurredAt, List.of(new StockEntryResponse.Line(item, new BigDecimal("1.50")))));
    }
  }

  private ConfigurableApplicationContext startApplication() {
    return new SpringApplicationBuilder(VestigiumApplication.class)
        .bannerMode(Banner.Mode.OFF)
        .logStartupInfo(false)
        .run(
            "--server.port=0",
            "--spring.datasource.url=" + postgres.getJdbcUrl(),
            "--spring.datasource.username=" + postgres.getUsername(),
            "--spring.datasource.password=" + postgres.getPassword());
  }

  private UUID registerItem(PhysicalUnit unit) throws Exception {
    UUID id = UUID.randomUUID();
    String body =
        json.writeValueAsString(
            Map.of("id", id.toString(), "name", "Synthetic entry fixture", "unit", unit.name()));
    assertThat(send(port, "/items", body).statusCode()).isEqualTo(201);
    return id;
  }

  private static String line(UUID item, String quantity) {
    return "{\"itemId\":\"" + item + "\",\"quantity\":" + quantity + "}";
  }

  private static String entryJson(UUID id, String occurredAt, String... lines) {
    return "{\"id\":\""
        + id
        + "\",\"occurredAt\":\""
        + occurredAt
        + "\",\"lines\":["
        + String.join(",", lines)
        + "]}";
  }

  private void assertNoStoredEntry(UUID id) {
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM stock_entries WHERE id = ?", Long.class, id))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM stock_entry_lines WHERE entry_id = ?", Long.class, id))
        .isZero();
  }

  private void assertEntry(HttpResponse<String> response, StockEntryResponse expected)
      throws Exception {
    assertThat(response.statusCode()).isEqualTo(200);
    assertRepresentation(response, expected);
  }

  private void assertRepresentation(HttpResponse<String> response, StockEntryResponse expected)
      throws Exception {
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
    assertThat(JsonPath.parse(response.body()).<Map<String, Object>>json())
        .containsOnlyKeys("id", "occurredAt", "lines");
    assertThat(json.readValue(response.body(), StockEntryResponse.class)).isEqualTo(expected);
  }

  private static void assertProblem(HttpResponse<String> response, int status, String path) {
    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
    Map<String, Object> problem = JsonPath.parse(response.body()).json();
    assertThat(problem.getOrDefault("type", "about:blank")).isEqualTo("about:blank");
    assertThat(problem)
        .containsEntry("status", status)
        .containsEntry("instance", path)
        .doesNotContainKeys("trace", "exception");
    assertThat(problem.get("title")).isNotNull();
    assertThat(problem.get("detail")).isNotNull();
    assertThat(response.body())
        .doesNotContain(
            "org.hibernate",
            "SQLState",
            "JDBC",
            "entries_http_failure_probe",
            "stock_entry_lines_item_id_fkey");
  }

  private static HttpResponse<String> send(int port, String path, String body) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(5))
            .header("Accept", "application/json");
    if (body == null) builder.GET();
    else
      builder
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body));
    try (HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
  }
}
