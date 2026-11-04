package io.github.josepauloferreira.vestigium;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.josepauloferreira.vestigium.application.ItemRepository;
import io.github.josepauloferreira.vestigium.application.StockEntryRepository;
import io.github.josepauloferreira.vestigium.domain.*;
import io.github.josepauloferreira.vestigium.infrastructure.web.ItemBalanceResponse;
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

  @Test
  void returnsNotFoundForAnUnknownCatalogIdentity() throws Exception {
    String path = "/items/" + UUID.randomUUID() + "/balance";
    assertProblem(get(path), 404, path);
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid", "1-1-1-1-1"})
  void rejectsNoncanonicalBalanceIds(String id) throws Exception {
    String path = "/items/" + id + "/balance";
    assertProblem(get(path), 400, path);
  }

  @Test
  void returnsSafeServerFailureWhenTheHistoryCannotBeRead() throws Exception {
    ItemId item = item();
    String path = "/items/" + item.value() + "/balance";
    jdbc.execute("ALTER TABLE stock_entries RENAME TO item_stock_failure_probe");
    try {
      assertProblem(get(path), 500, path);
    } finally {
      jdbc.execute("ALTER TABLE item_stock_failure_probe RENAME TO stock_entries");
    }
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
