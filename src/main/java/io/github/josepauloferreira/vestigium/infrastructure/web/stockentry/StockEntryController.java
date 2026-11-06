package io.github.josepauloferreira.vestigium.infrastructure.web.stockentry;

import io.github.josepauloferreira.vestigium.application.stockentry.StockEntryRepository;
import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import io.github.josepauloferreira.vestigium.domain.stock.MovementQuantity;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntry;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntryId;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntryLine;
import io.github.josepauloferreira.vestigium.infrastructure.web.shared.CanonicalUuid;
import io.github.josepauloferreira.vestigium.infrastructure.web.shared.OccurrenceTime;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
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
    Instant occurredAt = OccurrenceTime.parse(request.occurredAt());
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

  private static StockEntryId parseId(String value) {
    return new StockEntryId(CanonicalUuid.parse(value, "Stock entry ID"));
  }
}
