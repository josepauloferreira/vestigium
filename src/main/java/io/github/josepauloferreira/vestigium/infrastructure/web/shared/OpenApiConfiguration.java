package io.github.josepauloferreira.vestigium.infrastructure.web.shared;

import io.github.josepauloferreira.vestigium.infrastructure.web.stockentry.CreateStockEntryRequest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {
  @Bean
  OpenAPI vestigiumApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Vestigium API")
                .version("0.0.1-SNAPSHOT")
                .description(
                    "Records catalog items and complete material receipts. Quantities derive from optional initial stock plus recorded receipts. Initial stock and receipt history are independently retrievable; exits, returns and adjustments are future work. Example data is synthetic."));
  }

  @Bean
  OpenApiCustomizer accurateBusinessContract() {
    return api -> {
      var field =
          new ObjectSchema()
              .addProperty("field", new StringSchema())
              .addProperty("message", new StringSchema())
              .required(List.of("field", "message"));
      var problem =
          new ObjectSchema()
              .addProperty(
                  "type", new StringSchema().description("Defaults to about:blank when omitted"))
              .addProperty("title", new StringSchema())
              .addProperty("status", new IntegerSchema())
              .addProperty("detail", new StringSchema())
              .addProperty("instance", new StringSchema())
              .addProperty("errors", new ArraySchema().items(field))
              .required(List.of("title", "status", "detail", "instance"));
      api.getComponents().addSchemas("ApiProblem", problem);
      api.getPaths()
          .forEach(
              (path, item) ->
                  item.readOperationsMap()
                      .forEach(
                          (method, operation) -> {
                            if (operation.getParameters() != null) {
                              operation.getParameters().stream()
                                  .filter(
                                      parameter ->
                                          "path".equals(parameter.getIn())
                                              && "id".equals(parameter.getName()))
                                  .forEach(
                                      parameter ->
                                          parameter.setSchema(
                                              new StringSchema()
                                                  .format("uuid")
                                                  .pattern(CreateStockEntryRequest.UUID_PATTERN)));
                            }
                            var responses = operation.getResponses();
                            responses.addApiResponse(
                                "400", error("Invalid request, fields or canonical UUID"));
                            responses.addApiResponse(
                                "500",
                                error(
                                    "Unexpected persistence failure; diagnostics are not exposed"));
                            if (method == io.swagger.v3.oas.models.PathItem.HttpMethod.GET) {
                              responses.addApiResponse(
                                  "404",
                                  error(
                                      "Catalog item, receipt or recorded initial stock does not exist"));
                            } else if (method
                                == io.swagger.v3.oas.models.PathItem.HttpMethod.POST) {
                              var created = responses.remove("200");
                              if (created == null) created = responses.get("201");
                              if (created == null)
                                throw new IllegalStateException(
                                    "Missing generated creation response");
                              created
                                  .description("Created without overwriting existing facts")
                                  .addHeaderObject(
                                      "Location",
                                      new Header()
                                          .description(
                                              "Relative GET address of the created resource")
                                          .schema(new StringSchema()));
                              responses.addApiResponse("201", created);
                              responses.addApiResponse(
                                  "409",
                                  error(
                                      "Identity already exists or receipt chronology conflicts with initial stock; original facts are preserved"));
                              if (path.endsWith("/initial-stock")) {
                                responses.addApiResponse(
                                    "404", error("Catalog item does not exist"));
                                responses.addApiResponse(
                                    "422",
                                    error(
                                        "Initial quantity or occurrence exceeds supported exact storage limits"));
                              }
                              if (path.equals("/stock-entries")) {
                                responses.addApiResponse(
                                    "422",
                                    error(
                                        "Unknown catalog item or a value outside supported exact storage limits"));
                              }
                            }
                          }));
      property(api, "CreateInitialStockRequest", "quantity")
          .setDescription(
              "Nonnegative exact decimal JSON number, including zero. Quoted numbers are rejected. Preserves original scale without rounding.");
      property(api, "CreateInitialStockRequest", "quantity")
          .setExample(new java.math.BigDecimal("50.00"));
      property(api, "CreateInitialStockRequest", "occurredAt")
          .setDescription(
              "ISO 8601 date/time with Z or explicit offset, exactly representable in microseconds. Must precede or equal all this item's receipts; receipts at the same instant follow the opening.");
      property(api, "CreateInitialStockRequest", "occurredAt").setExample("2026-11-03T08:00:00Z");
      // Java's code-point regex is not valid JSON Schema/ECMAScript syntax.
      var name = property(api, "CreateItemRequest", "name");
      name.setPattern(null);
      name.setDescription(
          "Nonblank valid Unicode without NUL or unpaired surrogates. Whitespace/case are preserved.");
      name.setExample("Synthetic hydraulic oil");
      for (String component : List.of("CreateItemRequest", "CreateStockEntryRequest")) {
        property(api, component, "id").setFormat("uuid");
        property(api, component, "id").setPattern(CreateStockEntryRequest.UUID_PATTERN);
      }
      property(api, "StockEntryRequestLine", "itemId").setFormat("uuid");
      property(api, "CreateItemRequest", "id").setExample("00000000-0000-0000-0000-000000000201");
      property(api, "CreateStockEntryRequest", "id")
          .setExample("00000000-0000-0000-0000-000000000301");
      property(api, "CreateStockEntryRequest", "occurredAt")
          .setDescription(
              "ISO 8601 date/time with Z or an explicit offset, exactly representable in microseconds. Returned as a UTC instant; no automatic future cutoff. A receipt cannot precede established initial stock for any referenced item.");
      property(api, "CreateStockEntryRequest", "occurredAt")
          .setExample("2026-11-03T14:30:00.123456Z");
      property(api, "StockEntryRequestLine", "itemId")
          .setExample("00000000-0000-0000-0000-000000000201");
      property(api, "StockEntryRequestLine", "quantity")
          .setDescription(
              "Positive exact decimal JSON number; quoted numbers are rejected. Uses the item's physical unit; no fixed scale or rounding.");
      property(api, "StockEntryRequestLine", "quantity")
          .setExample(new java.math.BigDecimal("1.50"));
    };
  }

  private static Schema<?> property(OpenAPI api, String component, String property) {
    return (Schema<?>)
        api.getComponents().getSchemas().get(component).getProperties().get(property);
  }

  private static ApiResponse error(String description) {
    return new ApiResponse()
        .description(description)
        .content(
            new Content()
                .addMediaType(
                    "application/problem+json",
                    new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ApiProblem"))));
  }
}
