package io.github.josepauloferreira.vestigium.infrastructure.web.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.josepauloferreira.vestigium.PostgresTestConfiguration;
import io.github.josepauloferreira.vestigium.VestigiumApplication;
import io.github.josepauloferreira.vestigium.application.item.ItemRepository;
import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import io.github.josepauloferreira.vestigium.domain.item.PhysicalUnit;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
class ItemHttpTest {

  @LocalServerPort private int port;
  @Autowired private JsonMapper json;
  @Autowired private ItemRepository repository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PostgreSQLContainer postgres;

  @ParameterizedTest
  @EnumSource(PhysicalUnit.class)
  void registersAndQueriesEachPhysicalUnit(PhysicalUnit unit) throws Exception {
    UUID id = UUID.randomUUID();
    String body = itemJson(id, "Synthetic material", unit.name());

    HttpResponse<String> created = post(body);

    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(created.headers().firstValue("Location")).hasValue("/items/" + id);
    assertRepresentation(created, id, "Synthetic material", unit.name());
    assertItem(get("/items/" + id), id, "Synthetic material", unit.name());
    assertThat(repository.findById(new ItemId(id))).isPresent();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "  Synthetic OIL  ",
        "Óleo hidráulico 🛠",
        "D'água; DROP TABLE items; --",
        "\tSynthetic\nmaterial\t"
      })
  void preservesNamesWithoutNormalization(String name) throws Exception {
    UUID id = UUID.randomUUID();

    assertThat(post(itemJson(id, name, "LITRE")).statusCode()).isEqualTo(201);

    assertItem(get("/items/" + id), id, name, "LITRE");
  }

  @Test
  void preservesLongNames() throws Exception {
    UUID id = UUID.randomUUID();
    String name = "Synthetic material ".repeat(100);

    assertThat(post(itemJson(id, name, "KG")).statusCode()).isEqualTo(201);

    assertItem(get("/items/" + id), id, name, "KG");
  }

  @ParameterizedTest
  @MethodSource("invalidBodies")
  void rejectsInvalidRequestsWithoutWritingAnItem(String template) throws Exception {
    UUID id = UUID.randomUUID();
    Long count = jdbc.queryForObject("SELECT count(*) FROM items", Long.class);

    HttpResponse<String> response = post(template.replace("$id", id.toString()));

    assertProblem(response, 400, "Bad Request", "/items");
    assertThat(repository.findById(new ItemId(id))).isEmpty();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM items", Long.class)).isEqualTo(count);
  }

  private static Stream<String> invalidBodies() {
    return Stream.of(
        "",
        "null",
        "{",
        "[]",
        "{}",
        "{\"name\":\"Synthetic material\",\"unit\":\"LITRE\"}",
        "{\"id\":\"$id\",\"unit\":\"LITRE\"}",
        "{\"id\":\"$id\",\"name\":\"Synthetic material\"}",
        "{\"id\":null,\"name\":\"Synthetic material\",\"unit\":\"LITRE\"}",
        "{\"id\":\"$id\",\"name\":null,\"unit\":\"LITRE\"}",
        "{\"id\":\"$id\",\"name\":\"Synthetic material\",\"unit\":null}",
        "{\"id\":\"$id\",\"name\":\"\",\"unit\":\"LITRE\"}",
        "{\"id\":\"$id\",\"name\":\" \\t\\n\",\"unit\":\"LITRE\"}",
        "{\"id\":\"$id\",\"name\":\"\\u2003\",\"unit\":\"LITRE\"}",
        "{\"id\":\"$id\",\"name\":\"Synthetic material\",\"unit\":\"BOX\"}",
        "{\"id\":\"$id\",\"name\":\"Synthetic material\",\"unit\":2}",
        "{\"id\":\"$id\",\"name\":\"Synthetic material\",\"unit\":\"2\"}",
        "{\"id\":\"$id\",\"name\":\"Synthetic material\",\"unit\":true}",
        "{\"id\":\"$id\",\"name\":42,\"unit\":\"LITRE\"}",
        "{\"id\":\"$id\",\"name\":false,\"unit\":\"LITRE\"}",
        "{\"id\":42,\"name\":\"Synthetic material\",\"unit\":\"LITRE\"}",
        "{\"id\":\"not-a-uuid\",\"name\":\"Synthetic material\",\"unit\":\"LITRE\"}",
        "{\"id\":\"1-1-1-1-1\",\"name\":\"Synthetic material\",\"unit\":\"LITRE\"}",
        "{\"id\":\"$id\",\"name\":\"Synthetic\\u0000material\",\"unit\":\"LITRE\"}",
        "{\"id\":\"$id\",\"name\":\"Synthetic\\uD800material\",\"unit\":\"LITRE\"}",
        "{\"id\":\"$id\",\"name\":\"Synthetic\\uDFFFmaterial\",\"unit\":\"LITRE\"}");
  }

  @Test
  void includesUsefulFieldValidationErrors() throws Exception {
    HttpResponse<String> response = post("{}");

    assertProblem(response, 400, "Bad Request", "/items");
    assertThat(JsonPath.parse(response.body()).<String>read("$.detail"))
        .isEqualTo("Request fields are invalid");
    assertThat(JsonPath.parse(response.body()).<java.util.List<String>>read("$.errors[*].field"))
        .containsExactly("id", "name", "unit");
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-a-uuid", "1-1-1-1-1"})
  void rejectsInvalidIdentityPaths(String id) throws Exception {
    assertProblem(get("/items/" + id), 400, "Bad Request", "/items/" + id);
  }

  @Test
  void returnsNotFoundForAnUnknownIdentity() throws Exception {
    String path = "/items/" + UUID.randomUUID();

    assertProblem(get(path), 404, "Not Found", path);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void rejectsDuplicateIdsWithoutOverwritingTheOriginal(boolean identical) throws Exception {
    UUID id = UUID.randomUUID();
    String original = itemJson(id, "Original material", "KG");
    assertThat(post(original).statusCode()).isEqualTo(201);

    HttpResponse<String> duplicate =
        post(identical ? original : itemJson(id, "Replacement material", "ROLL"));

    assertProblem(duplicate, 409, "Conflict", "/items");
    assertItem(get("/items/" + id), id, "Original material", "KG");
  }

  @Test
  void allowsDifferentIdsWithTheSameName() throws Exception {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();

    assertThat(post(itemJson(first, "Synthetic material", "KG")).statusCode()).isEqualTo(201);
    assertThat(post(itemJson(second, "Synthetic material", "UNIT")).statusCode()).isEqualTo(201);

    assertItem(get("/items/" + first), first, "Synthetic material", "KG");
    assertItem(get("/items/" + second), second, "Synthetic material", "UNIT");
  }

  @Test
  void reportsUnexpectedDatabaseFailureAsServerError() throws Exception {
    String path = "/items/" + UUID.randomUUID();
    jdbc.execute("ALTER TABLE items RENAME TO items_http_failure_probe");
    try {
      HttpResponse<String> response = get(path);
      assertProblem(response, 500, "Internal Server Error", path);
      assertThat(JsonPath.parse(response.body()).<String>read("$.detail"))
          .isEqualTo("Unable to complete the request");
    } finally {
      jdbc.execute("ALTER TABLE items_http_failure_probe RENAME TO items");
    }
  }

  @Test
  void queriesPersistedItemAfterClosingAndReopeningTheHttpApplication() throws Exception {
    UUID id = UUID.randomUUID();
    try (ConfigurableApplicationContext first = startApplication()) {
      int firstPort =
          first.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
      HttpResponse<String> created =
          send(firstPort, "/items", itemJson(id, "Restart fixture", "LITRE"));
      assertThat(created.statusCode()).isEqualTo(201);
    }
    try (ConfigurableApplicationContext second = startApplication()) {
      int secondPort =
          second.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
      assertItem(send(secondPort, "/items/" + id, null), id, "Restart fixture", "LITRE");
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

  private String itemJson(UUID id, String name, String unit) throws Exception {
    return json.writeValueAsString(Map.of("id", id.toString(), "name", name, "unit", unit));
  }

  private HttpResponse<String> post(String body) throws IOException, InterruptedException {
    return send(port, "/items", body);
  }

  private HttpResponse<String> get(String path) throws IOException, InterruptedException {
    return send(port, path, null);
  }

  private static HttpResponse<String> send(int port, String path, String body)
      throws IOException, InterruptedException {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(5))
            .header("Accept", "application/json");
    if (body == null) {
      builder.GET();
    } else {
      builder
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body));
    }
    try (HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
  }

  private static void assertItem(HttpResponse<String> response, UUID id, String name, String unit) {
    assertThat(response.statusCode()).isEqualTo(200);
    assertRepresentation(response, id, name, unit);
  }

  private static void assertRepresentation(
      HttpResponse<String> response, UUID id, String name, String unit) {
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
    assertThat(JsonPath.parse(response.body()).<Object>json())
        .isEqualTo(Map.of("id", id.toString(), "name", name, "unit", unit));
  }

  private static void assertProblem(
      HttpResponse<String> response, int status, String title, String path) {
    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
    assertThat(JsonPath.parse(response.body()).<Integer>read("$.status")).isEqualTo(status);
    Map<String, Object> problem = JsonPath.parse(response.body()).json();
    assertThat(problem.getOrDefault("type", "about:blank")).isEqualTo("about:blank");
    assertThat(problem).doesNotContainKeys("trace", "exception");
    assertThat(JsonPath.parse(response.body()).<String>read("$.title")).isEqualTo(title);
    assertThat(JsonPath.parse(response.body()).<String>read("$.instance")).isEqualTo(path);
    assertThat(JsonPath.parse(response.body()).<String>read("$.detail")).isNotBlank();
    assertThat(response.body())
        .doesNotContain("SQL", "JDBC", "org.hibernate", "stackTrace", "items_http_failure_probe");
  }
}
