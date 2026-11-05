package io.github.josepauloferreira.vestigium.infrastructure.web;

import io.github.josepauloferreira.vestigium.application.ItemStockQueries;
import io.github.josepauloferreira.vestigium.domain.ItemId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Item stock")
@RestController
@RequestMapping(value = "/items/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ItemStockController {
  private final ItemStockQueries queries;

  @Operation(
      operationId = "getItemBalance",
      summary = "Calculate initial stock plus receipts",
      description =
          "Exact total of optional initial stock plus all recorded receipts, including future-dated facts. No automatic time cutoff. An existing item without initial stock or receipts returns zero. Other movement types are future work.")
  @GetMapping("/balance")
  public ItemBalanceResponse balance(@PathVariable String id) {
    var balance = queries.balance(parseId(id));
    return new ItemBalanceResponse(balance.itemId().value(), balance.unit(), balance.quantity());
  }

  private static ItemId parseId(String value) {
    try {
      UUID id = UUID.fromString(value);
      if (!id.toString().equalsIgnoreCase(value)) {
        throw new IllegalArgumentException("Noncanonical UUID");
      }
      return new ItemId(id);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item ID must be a canonical UUID");
    }
  }

  @Operation(
      operationId = "getItemEntryHistory",
      summary = "List an item's recorded receipts",
      description =
          "All recorded receipts for this item, ordered by occurrence and database UUID order. Preserves each quantity scale. Existing items without receipts return an empty list; no pagination or date filter is applied. Initial stock is retrieved separately and also contributes to balance.")
  @GetMapping("/stock-entries")
  public ItemEntryHistoryResponse history(@PathVariable String id) {
    var history = queries.history(parseId(id));
    return new ItemEntryHistoryResponse(
        history.itemId().value(),
        history.unit(),
        history.entries().stream()
            .map(
                entry ->
                    new ItemEntryHistoryResponse.Entry(
                        entry.entryId().value(), entry.occurredAt().toString(), entry.quantity()))
            .toList());
  }
}
