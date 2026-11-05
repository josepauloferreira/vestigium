package io.github.josepauloferreira.vestigium.infrastructure.web;

import io.github.josepauloferreira.vestigium.application.StockEntryRepository;
import io.github.josepauloferreira.vestigium.domain.ItemId;
import io.github.josepauloferreira.vestigium.domain.MovementQuantity;
import io.github.josepauloferreira.vestigium.domain.StockEntry;
import io.github.josepauloferreira.vestigium.domain.StockEntryId;
import io.github.josepauloferreira.vestigium.domain.StockEntryLine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;
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

@Tag(name = "Stock entries")
@RestController
@RequestMapping(value = "/stock-entries", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class StockEntryController {

  private final StockEntryRepository repository;

  @Operation(
      operationId = "createStockEntry",
      summary = "Record a complete receipt",
      description =
          "Atomic ordered lines for existing catalog items. Quantities are exact JSON numbers. Requires an explicit offset or Z and microsecond precision; repeated items are rejected without summing. Occurrence cannot precede any referenced item's established initial stock.")
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<StockEntryResponse> create(
      @Valid @RequestBody CreateStockEntryRequest request) {
    Instant occurredAt = parseOccurrence(request.occurredAt());
    var lines =
        request.lines().stream()
            .map(
                line ->
                    new StockEntryLine(
                        new ItemId(UUID.fromString(line.itemId())),
                        new MovementQuantity(line.quantity())))
            .toList();
    StockEntry entry;
    try {
      entry = new StockEntry(new StockEntryId(UUID.fromString(request.id())), occurredAt, lines);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
    repository.insert(entry);
    return ResponseEntity.created(URI.create("/stock-entries/" + entry.id().value()))
        .body(StockEntryResponse.from(entry));
  }

  @Operation(operationId = "getStockEntry", summary = "Find a complete receipt")
  @GetMapping("/{id}")
  public StockEntryResponse find(@PathVariable String id) {
    return repository
        .findById(parseId(id))
        .map(StockEntryResponse::from)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock entry was not found"));
  }

  private static Instant parseOccurrence(String value) {
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (DateTimeParseException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "occurredAt must be an ISO 8601 date/time with Z or an explicit offset");
    }
  }

  private static StockEntryId parseId(String value) {
    try {
      UUID id = UUID.fromString(value);
      if (!id.toString().equalsIgnoreCase(value)) {
        throw new IllegalArgumentException("Noncanonical UUID");
      }
      return new StockEntryId(id);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Stock entry ID must be a canonical UUID");
    }
  }
}
