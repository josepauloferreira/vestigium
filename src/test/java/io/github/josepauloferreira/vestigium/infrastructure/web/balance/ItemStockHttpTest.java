package io.github.josepauloferreira.vestigium.infrastructure.web.balance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.josepauloferreira.vestigium.PostgresTestConfiguration;
import io.github.josepauloferreira.vestigium.application.item.ItemRepository;
import io.github.josepauloferreira.vestigium.application.stockentry.StockEntryRepository;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ItemStockHttpTest {
  @LocalServerPort private int port;
  @Autowired private ItemRepository items;
  @Autowired private StockEntryRepository entries;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;

  @Test
  void derivesAnExactBalanceFromOnlyTheRequestedItemsReceipts() throws Exception {
    ItemId item = item();
    ItemId other = item();
    insert(Instant.parse("2026-01-01T00:00:00Z"), line(other, "999"), line(item, "1.50"));
    insert(Instant.parse("2026-01-02T00:00:00Z"), line(item, "0.75"));
    insert(Instant.parse("2026-01-03T00:00:00Z"), line(other, "999"));

    ItemBalanceResponse balance = balance(item);

    assertThat(balance.itemId()).isEqualTo(item.value());
    assertThat(balance.unit()).isEqualTo(PhysicalUnit.LITRE);
    assertThat(balance.quantity()).isEqualByComparingTo("2.25");
    assertThat(balance(item).quantity()).isEqualByComparingTo("2.25");
  }

  @Test
  void returnsZeroForAnExistingItemWithoutReceipts() throws Exception {
    assertThat(balance(item()).quantity()).isZero();
  }

  @Test
  void includesFutureAndPastRecordedReceiptsWithoutAClockCutoff() throws Exception {
    ItemId item = item();
    insert(Instant.parse("1969-12-31T23:59:59.999999Z"), line(item, "1.50"));
    insert(Instant.parse("2100-01-01T00:00:00Z"), line(item, "0.75"));
    assertThat(balance(item).quantity()).isEqualByComparingTo("2.25");
  }

  @ParameterizedTest
  @ValueSource(strings = {"balance", "stock-entries"})
  void returnsNotFoundForAnUnknownCatalogIdentity(String route) throws Exception {
    String path = "/items/" + UUID.randomUUID() + "/" + route;
    assertProblem(get(path), 404, path);
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid", "1-1-1-1-1"})
  void rejectsNoncanonicalBalanceIds(String id) throws Exception {
    String path = "/items/" + id + "/balance";
    assertProblem(get(path), 400, path);
  }

  @ParameterizedTest
  @ValueSource(strings = {"balance", "stock-entries"})
  void returnsSafeServerFailureWhenTheHistoryCannotBeRead(String route) throws Exception {
    ItemId item = item();
    String path = "/items/" + item.value() + "/" + route;
    jdbc.execute("ALTER TABLE stock_entries RENAME TO item_stock_failure_probe");
    try {
      assertProblem(get(path), 500, path);
    } finally {
      jdbc.execute("ALTER TABLE item_stock_failure_probe RENAME TO stock_entries");
    }
  }

  @Test
  void explainsTheBalanceWithOrderedItemSpecificReceiptSummaries() throws Exception {
    ItemId item = item();
    ItemId other = item();
    Instant firstTime = Instant.parse("1969-12-31T23:59:59.999999Z");
    Instant laterTime = Instant.parse("2100-01-01T00:00:00Z");
    StockEntryId first = insert(firstTime, line(other, "999"), line(item, "1.50"));
    StockEntryId second = insert(laterTime, line(item, "0.7500"));
    insert(firstTime, line(other, "999"));

    ItemEntryHistoryResponse history = history(item);

    assertThat(history.itemId()).isEqualTo(item.value());
    assertThat(history.unit()).isEqualTo(PhysicalUnit.LITRE);
    assertThat(history.entries())
        .containsExactly(
            new ItemEntryHistoryResponse.Entry(
                first.value(), firstTime.toString(), new BigDecimal("1.50")),
            new ItemEntryHistoryResponse.Entry(
                second.value(), laterTime.toString(), new BigDecimal("0.7500")));
    assertThat(
            history.entries().stream()
                .map(ItemEntryHistoryResponse.Entry::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo(balance(item).quantity());
  }

  @Test
  void returnsEmptyHistoryForAKnownItemWithoutReceipts() throws Exception {
    assertThat(history(item()).entries()).isEmpty();
  }

  @Test
  void keepsTimestampTiesStableUsingTheDatabaseUuidOrder() throws Exception {
    ItemId item = item();
    Instant time = Instant.parse("2026-01-01T00:00:00Z");
    StockEntryId low = new StockEntryId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    StockEntryId high = new StockEntryId(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
    entries.insert(new StockEntry(high, time, List.of(line(item, "2"))));
    entries.insert(new StockEntry(low, time, List.of(line(item, "1"))));
    assertThat(history(item).entries())
        .extracting(ItemEntryHistoryResponse.Entry::entryId)
        .containsExactly(low.value(), high.value());
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid", "1-1-1-1-1"})
  void rejectsNoncanonicalHistoryIds(String id) throws Exception {
    String path = "/items/" + id + "/stock-entries";
    assertProblem(get(path), 400, path);
  }

  private ItemEntryHistoryResponse history(ItemId item) throws Exception {
    HttpResponse<String> response = get("/items/" + item.value() + "/stock-entries");
    assertThat(response.statusCode()).isEqualTo(200);
    return json.readValue(response.body(), ItemEntryHistoryResponse.class);
  }

  private ItemId item() {
    ItemId id = new ItemId(UUID.randomUUID());
    items.insert(new Item(id, "Synthetic stock query item", PhysicalUnit.LITRE));
    return id;
  }

  private StockEntryId insert(Instant time, StockEntryLine... lines) {
    StockEntryId id = new StockEntryId(UUID.randomUUID());
    entries.insert(new StockEntry(id, time, List.of(lines)));
    return id;
  }

  private static StockEntryLine line(ItemId item, String quantity) {
    return new StockEntryLine(item, new MovementQuantity(new BigDecimal(quantity)));
  }

  private ItemBalanceResponse balance(ItemId item) throws Exception {
    HttpResponse<String> response = get("/items/" + item.value() + "/balance");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(v -> assertThat(v).startsWith("application/json"));
    return json.readValue(response.body(), ItemBalanceResponse.class);
  }

  private void assertProblem(HttpResponse<String> response, int status, String path)
      throws Exception {
    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(v -> assertThat(v).startsWith("application/problem+json"));
    Map<?, ?> problem = json.readValue(response.body(), Map.class);
    assertThat(problem.get("status")).isEqualTo(status);
    assertThat(problem.get("instance")).isEqualTo(path);
    assertThat(response.body())
        .doesNotContain("org.hibernate", "JDBC", "item_stock_failure_probe", "stackTrace");
  }

  private HttpResponse<String> get(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
    try (HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }
}
