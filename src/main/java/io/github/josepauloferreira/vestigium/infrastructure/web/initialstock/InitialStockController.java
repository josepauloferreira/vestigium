package io.github.josepauloferreira.vestigium.infrastructure.web.initialstock;

import io.github.josepauloferreira.vestigium.application.initialstock.InitialStockOperations;
import io.github.josepauloferreira.vestigium.domain.initialstock.InitialStockQuantity;
import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import io.github.josepauloferreira.vestigium.infrastructure.web.shared.CanonicalUuid;
import io.github.josepauloferreira.vestigium.infrastructure.web.shared.OccurrenceTime;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(value = "/items/{id}/initial-stock", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Initial stock")
public class InitialStockController {
  private final InitialStockOperations operations;
  private final InitialStockWebMapper mapper;

  @Operation(
      operationId = "createInitialStock",
      summary = "Establish an item's initial stock",
      description =
          "At most once per catalog item, including explicit zero. Occurs immediately before receipts at the same instant. Cannot follow existing receipts; subsequent receipts cannot precede this opening. No overwrite.")
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<InitialStockResponse> create(
      @PathVariable String id, @Valid @RequestBody CreateInitialStockRequest request) {
    ItemId itemId = parseId(id);
    Instant occurredAt = OccurrenceTime.parse(request.occurredAt());
    if (occurredAt.getNano() % 1_000 != 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Initial stock occurrence must use microsecond precision");
    }
    var result =
        operations.register(itemId, new InitialStockQuantity(request.quantity()), occurredAt);
    return ResponseEntity.created(URI.create("/items/" + itemId.value() + "/initial-stock"))
        .body(mapper.toResponse(result));
  }

  @Operation(
      operationId = "getInitialStock",
      summary = "Find an item's recorded initial stock",
      description =
          "A recorded zero is a fact. An existing item without an opening record returns 404 with a distinct detail; absence never fabricates a zero opening.")
  @GetMapping
  public InitialStockResponse find(@PathVariable String id) {
    return mapper.toResponse(operations.find(parseId(id)));
  }

  private static ItemId parseId(String value) {
    return new ItemId(CanonicalUuid.parse(value, "Item ID"));
  }
}
