package io.github.josepauloferreira.vestigium.infrastructure.web.initialstock;

import io.github.josepauloferreira.vestigium.application.initialstock.InitialStockOperations;
import io.github.josepauloferreira.vestigium.domain.initialstock.InitialStockQuantity;
import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(value = "/items/{id}/initial-stock", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Initial stock")
public class InitialStockController {
  private final InitialStockOperations operations;

  @Operation(
      operationId = "createInitialStock",
      summary = "Establish an item's initial stock",
      description =
          "At most once per catalog item, including explicit zero. Occurs immediately before receipts at the same instant. Cannot follow existing receipts; subsequent receipts cannot precede this opening. No overwrite.")
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<InitialStockResponse> create(
      @PathVariable String id, @Valid @RequestBody CreateInitialStockRequest request) {
    ItemId itemId = parseId(id);
    java.time.Instant time;
    try {
      time = OffsetDateTime.parse(request.occurredAt()).toInstant();
    } catch (DateTimeParseException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "occurredAt must be an ISO 8601 date/time with Z or an explicit offset");
    }
    if (time.getNano() % 1_000 != 0)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Initial stock occurrence must use microsecond precision");
    var result = operations.register(itemId, new InitialStockQuantity(request.quantity()), time);
    return ResponseEntity.created(URI.create("/items/" + itemId.value() + "/initial-stock"))
        .body(InitialStockResponse.from(result));
  }

  @Operation(
      operationId = "getInitialStock",
      summary = "Find an item's recorded initial stock",
      description =
          "A recorded zero is a fact. An existing item without an opening record returns 404 with a distinct detail; absence never fabricates a zero opening.")
  @GetMapping
  public InitialStockResponse find(@PathVariable String id) {
    return InitialStockResponse.from(operations.find(parseId(id)));
  }

  private static ItemId parseId(String value) {
    try {
      UUID id = UUID.fromString(value);
      if (!id.toString().equalsIgnoreCase(value))
        throw new IllegalArgumentException("Noncanonical UUID");
      return new ItemId(id);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item ID must be a canonical UUID");
    }
  }
}
