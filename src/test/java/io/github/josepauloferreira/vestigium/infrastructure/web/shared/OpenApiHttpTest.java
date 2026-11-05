package io.github.josepauloferreira.vestigium.infrastructure.web.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.josepauloferreira.vestigium.PostgresTestConfiguration;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OpenApiHttpTest {
  @LocalServerPort private int port;

  @Test
  void describesAllBusinessRoutesAndTheirActualResponseStatuses() throws Exception {
    HttpResponse<String> response = get("/v3/api-docs");
    assertThat(response.statusCode()).isEqualTo(200);
    var spec = JsonPath.parse(response.body());
    assertThat(spec.<String>read("$.openapi")).startsWith("3.");
    assertThat(spec.<String>read("$.info.title")).isEqualTo("Vestigium API");
    assertThat(spec.<Map<String, Object>>read("$.paths"))
        .containsOnlyKeys(
            "/items",
            "/items/{id}",
            "/stock-entries",
            "/stock-entries/{id}",
            "/items/{id}/balance",
            "/items/{id}/stock-entries",
            "/items/{id}/initial-stock");
    assertThat(spec.<Map<String, Object>>read("$.paths['/items'].post.responses"))
        .containsOnlyKeys("201", "400", "409", "500");
    assertThat(spec.<Map<String, Object>>read("$.paths['/stock-entries'].post.responses"))
        .containsOnlyKeys("201", "400", "409", "422", "500");
    for (String path :
        new String[] {
          "/items/{id}",
          "/stock-entries/{id}",
          "/items/{id}/balance",
          "/items/{id}/stock-entries",
          "/items/{id}/initial-stock"
        }) {
      assertThat(spec.<Map<String, Object>>read("$.paths['" + path + "'].get.responses"))
          .containsOnlyKeys("200", "400", "404", "500");
    }
    assertThat(
            spec.<Map<String, Object>>read("$.paths['/items/{id}/initial-stock'].post.responses"))
        .containsOnlyKeys("201", "400", "404", "409", "422", "500");
    assertThat(
            spec.<String>read(
                "$.paths['/stock-entries'].post.responses['422'].content['application/problem+json'].schema['$ref']"))
        .isEqualTo("#/components/schemas/ApiProblem");
    assertThat(spec.<Map<String, Object>>read("$.paths['/items'].post.responses['201'].headers"))
        .containsKey("Location");
  }

  @Test
  void preservesExactNumericContractsRequiredLinesAndDistinctLineSchemas() throws Exception {
    var spec = JsonPath.parse(get("/v3/api-docs").body());
    assertThat(
            spec.<java.util.List<String>>read(
                "$.components.schemas.CreateStockEntryRequest.required"))
        .contains("id", "occurredAt", "lines");
    assertThat(
            spec.<Map<String, Object>>read(
                "$.components.schemas.StockEntryRequestLine.properties.quantity"))
        .containsEntry("type", "number")
        .doesNotContainKey("format");
    assertThat(
            spec.<Map<String, Object>>read(
                "$.components.schemas.ItemBalanceResponse.properties.quantity"))
        .containsEntry("type", "number")
        .doesNotContainKey("format");
    assertThat(spec.<String>read("$.components.schemas.CreateItemRequest.properties.id.format"))
        .isEqualTo("uuid");
    assertThat(spec.<String>read("$.paths['/items/{id}/balance'].get.parameters[0].schema.format"))
        .isEqualTo("uuid");
    assertThat(spec.<Map<String, Object>>read("$.components.schemas"))
        .containsKeys("StockEntryRequestLine", "StockEntryResponseLine", "ApiProblem");
    assertThat(
            spec.<Map<String, Object>>read(
                "$.components.schemas.CreateItemRequest.properties.name"))
        .doesNotContainKey("pattern");
    assertThat(
            spec.<String>read(
                "$.components.schemas.CreateStockEntryRequest.properties.occurredAt.description"))
        .contains("microseconds", "explicit offset");
    assertThat(
            spec.<java.util.List<String>>read(
                "$.components.schemas.CreateInitialStockRequest.required"))
        .containsExactlyInAnyOrder("quantity", "occurredAt");
    assertThat(
            spec.<Map<String, Object>>read(
                "$.components.schemas.CreateInitialStockRequest.properties.quantity"))
        .containsEntry("type", "number")
        .doesNotContainKey("format");
    assertThat(
            spec.<Number>read(
                    "$.components.schemas.CreateInitialStockRequest.properties.quantity.minimum")
                .intValue())
        .isZero();
    assertThat(spec.<String>read("$.paths['/items/{id}/initial-stock'].post.description"))
        .contains("explicit zero", "Cannot follow existing receipts");
    assertThat(spec.<String>read("$.paths['/items/{id}/balance'].get.description"))
        .contains("initial stock");
    assertThat(spec.<String>read("$.paths['/items/{id}/balance'].get.description"))
        .contains("future-dated", "No automatic time cutoff");
  }

  @Test
  void servesYamlAndSwaggerUiAssetsUsingTheApplicationsOwnDescription() throws Exception {
    HttpResponse<String> yaml = get("/v3/api-docs.yaml");
    assertThat(yaml.statusCode()).isEqualTo(200);
    assertThat(yaml.body()).contains("openapi:", "Vestigium API");
    HttpResponse<String> ui = get("/swagger-ui/index.html");
    assertThat(ui.statusCode()).isEqualTo(200);
    assertThat(ui.body()).contains("swagger-ui-bundle.js");
    assertThat(get("/swagger-ui/swagger-ui-bundle.js").statusCode()).isEqualTo(200);
    assertThat(get("/swagger-ui/swagger-ui.css").statusCode()).isEqualTo(200);
    assertThat(get("/v3/api-docs/swagger-config").body())
        .contains("/v3/api-docs")
        .doesNotContain("petstore");
  }

  private HttpResponse<String> get(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build();
    try (HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }
}
