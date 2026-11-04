package io.github.josepauloferreira.vestigium.infrastructure.web;

import io.github.josepauloferreira.vestigium.application.ItemStockQueries;
import io.github.josepauloferreira.vestigium.domain.ItemId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(value = "/items/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ItemStockController {
  private final ItemStockQueries queries;

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
}
