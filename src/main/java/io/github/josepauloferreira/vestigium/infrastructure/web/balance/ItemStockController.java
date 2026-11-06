package io.github.josepauloferreira.vestigium.infrastructure.web.balance;

import io.github.josepauloferreira.vestigium.application.balance.ItemStockQueries;
import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import io.github.josepauloferreira.vestigium.infrastructure.web.shared.CanonicalUuid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    return ItemBalanceResponse.from(queries.balance(parseId(id)));
  }

  private static ItemId parseId(String value) {
    return new ItemId(CanonicalUuid.parse(value, "Item ID"));
  }

  @Operation(
      operationId = "getItemEntryHistory",
      summary = "List an item's recorded receipts",
      description =
          "All recorded receipts for this item, ordered by occurrence and database UUID order. Preserves each quantity scale. Existing items without receipts return an empty list; no pagination or date filter is applied. Initial stock is retrieved separately and also contributes to balance.")
  @GetMapping("/stock-entries")
  public ItemEntryHistoryResponse history(@PathVariable String id) {
    return ItemEntryHistoryResponse.from(queries.history(parseId(id)));
  }
}
