package io.github.josepauloferreira.vestigium.infrastructure.web.initialstock;

import static org.assertj.core.api.Assertions.*;

import com.jayway.jsonpath.JsonPath;
import io.github.josepauloferreira.vestigium.PostgresTestConfiguration;
import io.github.josepauloferreira.vestigium.application.initialstock.InitialStockRepository;
import io.github.josepauloferreira.vestigium.application.item.ItemRepository;
import io.github.josepauloferreira.vestigium.application.stockentry.StockEntryRepository;
import io.github.josepauloferreira.vestigium.domain.initialstock.InitialStock;
import io.github.josepauloferreira.vestigium.domain.initialstock.InitialStockQuantity;
import io.github.josepauloferreira.vestigium.domain.item.Item;
import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import io.github.josepauloferreira.vestigium.domain.item.PhysicalUnit;
import io.github.josepauloferreira.vestigium.domain.stock.MovementQuantity;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntry;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntryId;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntryLine;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InitialStockHttpTest {
  @LocalServerPort int port;
  @Autowired ItemRepository items;
  @Autowired JsonMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired InitialStockRepository stocks;
  @Autowired StockEntryRepository entries;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
  private final String time = "2026-01-01T08:00:00.123456Z";

  @Test
  void recordsOpeningAndDerivesBalanceWithReceiptHistoryKeptSeparate() throws Exception {
    var id = item();
    String path = path(id);
    var created = call("POST", path, body("50.00", time));
    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(created.headers().firstValue("Location")).hasValue(path);
    var stock = json.readValue(created.body(), InitialStockResponse.class);
    assertThat(stock.itemId()).isEqualTo(id);
    assertThat(stock.unit()).isEqualTo(PhysicalUnit.LITRE);
    assertThat(stock.quantity()).isEqualTo(new BigDecimal("50.00"));
    assertThat(stock.occurredAt()).isEqualTo(time);
    assertThat(call("GET", path, null).body()).isEqualTo(created.body());
    assertThat(postReceipt(id, "10.50", time).statusCode()).isEqualTo(201);
    assertThat(
            JsonPath.parse(call("GET", "/items/" + id + "/balance", null).body())
                .<Number>read("$.quantity")
                .toString())
        .isEqualTo("60.5");
    assertThat(
            JsonPath.parse(call("GET", "/items/" + id + "/stock-entries", null).body())
                .<List<?>>read("$.entries"))
        .hasSize(1);
    assertThat(call("GET", path, null).body()).isEqualTo(created.body());
  }

  @Test
  void distinguishesMissingCatalogAbsentOpeningAndRecordedZero() throws Exception {
    var id = item();
    var absent = call("GET", path(id), null);
    problem(absent, 404);
    assertThat(absent.body()).contains("has not been recorded");
    var unknown = call("GET", path(UUID.randomUUID()), null);
    problem(unknown, 404);
    assertThat(unknown.body()).contains("Item was not found");
    problem(call("POST", path(UUID.randomUUID()), body("0", time)), 404);
    assertThat(call("POST", path(id), body("0.0000", time)).statusCode()).isEqualTo(201);
    assertThat(
            json.readValue(call("GET", path(id), null).body(), InitialStockResponse.class)
                .quantity())
        .isEqualTo(new BigDecimal("0.0000"));
  }

  @Test
  void duplicateNeverOverwritesTheOriginal() throws Exception {
    var id = item();
    var first = call("POST", path(id), body("1.50", time));
    problem(call("POST", path(id), body("99", time)), 409);
    problem(call("POST", path(id), body("1.50", time)), 409);
    assertThat(call("GET", path(id), null).body()).isEqualTo(first.body());
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "null", "\"1.5\"", "true", "{}"})
  void rejectsInvalidQuantitiesWithoutWriting(String value) throws Exception {
    var id = item();
    problem(call("POST", path(id), body(value, time)), 400);
    problem(call("GET", path(id), null), 404);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2026-01-01",
        "2026-01-01T08:00:00",
        "2026-01-01T08:00:00.0000001Z",
        "invalid",
        ""
      })
  void rejectsInvalidOccurrence(String value) throws Exception {
    var id = item();
    problem(call("POST", path(id), body("1", value)), 400);
    problem(call("GET", path(id), null), 404);
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid", "1-1-1-1-1"})
  void rejectsNoncanonicalIds(String id) throws Exception {
    problem(call("POST", "/items/" + id + "/initial-stock", body("0", time)), 400);
    problem(call("GET", "/items/" + id + "/initial-stock", null), 400);
  }

  @Test
  void normalizesOffsetsWithoutRounding() throws Exception {
    var id = item();
    var result = call("POST", path(id), body("1.50", "2026-01-01T05:00:00.123456-03:00"));
    assertThat(result.statusCode()).isEqualTo(201);
    assertThat(json.readValue(result.body(), InitialStockResponse.class).occurredAt())
        .isEqualTo(time);
  }

  @Test
  void rejectsBothChronologyConflictsAndKeepsExistingFacts() throws Exception {
    var id = item();
    assertThat(postReceipt(id, "1", time).statusCode()).isEqualTo(201);
    problem(call("POST", path(id), body("10", "2026-01-01T09:00:00Z")), 409);
    problem(call("GET", path(id), null), 404);
    assertThat(call("POST", path(id), body("10", time)).statusCode()).isEqualTo(201);
    problem(postReceipt(id, "1", "2026-01-01T07:00:00Z"), 409);
    assertThat(
            JsonPath.parse(call("GET", "/items/" + id + "/stock-entries", null).body())
                .<List<?>>read("$.entries"))
        .hasSize(1);
  }

  @Test
  void rejectsUnsupportedStorageWithoutCreatingAnOpening() throws Exception {
    var id = item();
    problem(call("POST", path(id), body("1E-16384", time)), 422);
    problem(call("GET", path(id), null), 404);
    problem(call("POST", path(id), body("1", "+300000-01-01T00:00:00Z")), 422);
  }

  @ParameterizedTest
  @ValueSource(strings = {"GET", "POST"})
  void returnsSafeUnexpectedStorageFailures(String method) throws Exception {
    var id = item();
    jdbc.execute("ALTER TABLE initial_stock RENAME TO opening_failure_probe");
    try {
      problem(call(method, path(id), "POST".equals(method) ? body("1", time) : null), 500);
    } finally {
      jdbc.execute("ALTER TABLE opening_failure_probe RENAME TO initial_stock");
    }
  }

  @Test
  void balanceUsesOneSnapshotWhileOpeningAndReceiptsCommitTogether() throws Exception {
    var id = item();
    assertThat(postReceipt(id, "10", time).statusCode()).isEqualTo(201);
    try (var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      var future =
          new java.util.concurrent.atomic.AtomicReference<
              java.util.concurrent.Future<HttpResponse<String>>>();
      new org.springframework.transaction.support.TransactionTemplate(transactionManager)
          .executeWithoutResult(
              tx -> {
                jdbc.execute("LOCK TABLE initial_stock IN ACCESS EXCLUSIVE MODE");
                future.set(pool.submit(() -> call("GET", "/items/" + id + "/balance", null)));
                long deadline =
                    System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                  if (Boolean.TRUE.equals(
                      jdbc.queryForObject(
                          "SELECT EXISTS (SELECT 1 FROM pg_locks WHERE relation='initial_stock'::regclass AND NOT granted)",
                          Boolean.class))) {
                    waiting = true;
                    break;
                  }
                  try {
                    Thread.sleep(10);
                  } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                  }
                }
                assertThat(waiting)
                    .as("the HTTP balance query really reached the blocked opening read")
                    .isTrue();
                var itemId = new ItemId(id);
                stocks.insert(
                    new InitialStock(
                        itemId,
                        InitialStockQuantity.of(new BigDecimal("50")),
                        java.time.Instant.parse(time)));
                entries.insert(
                    new StockEntry(
                        new StockEntryId(UUID.randomUUID()),
                        java.time.Instant.parse(time),
                        List.of(
                            new StockEntryLine(
                                itemId, new MovementQuantity(new BigDecimal("20"))))));
              });
      var before = future.get().get(10, java.util.concurrent.TimeUnit.SECONDS);
      assertThat(before.statusCode()).isEqualTo(200);
      assertThat(
              json.readValue(
                      before.body(),
                      io.github.josepauloferreira.vestigium.infrastructure.web.balance
                          .ItemBalanceResponse.class)
                  .quantity())
          .isEqualByComparingTo("10");
      var after = call("GET", "/items/" + id + "/balance", null);
      assertThat(
              json.readValue(
                      after.body(),
                      io.github.josepauloferreira.vestigium.infrastructure.web.balance
                          .ItemBalanceResponse.class)
                  .quantity())
          .isEqualByComparingTo("80");
    }
  }

  private UUID item() {
    var id = UUID.randomUUID();
    items.insert(
        new Item(new ItemId(id), "Synthetic initial stock HTTP material", PhysicalUnit.LITRE));
    return id;
  }

  private String path(UUID id) {
    return "/items/" + id + "/initial-stock";
  }

  private String body(String quantity, String time) {
    return "{\"quantity\":" + quantity + ",\"occurredAt\":\"" + time + "\"}";
  }

  private HttpResponse<String> postReceipt(UUID id, String quantity, String time) throws Exception {
    return call(
        "POST",
        "/stock-entries",
        "{\"id\":\""
            + UUID.randomUUID()
            + "\",\"occurredAt\":\""
            + time
            + "\",\"lines\":[{\"itemId\":\""
            + id
            + "\",\"quantity\":"
            + quantity
            + "}]}");
  }

  private void problem(HttpResponse<String> response, int status) {
    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(v -> assertThat(v).startsWith("application/problem+json"));
    assertThat(JsonPath.parse(response.body()).<Integer>read("$.status")).isEqualTo(status);
    assertThat(response.body())
        .doesNotContain("org.hibernate", "JDBC", "opening_failure_probe", "stackTrace");
  }

  private HttpResponse<String> call(String method, String path, String body) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build();
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }
}
