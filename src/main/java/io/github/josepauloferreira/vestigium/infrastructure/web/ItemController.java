package io.github.josepauloferreira.vestigium.infrastructure.web;

import io.github.josepauloferreira.vestigium.application.ItemRepository;
import io.github.josepauloferreira.vestigium.domain.Item;
import io.github.josepauloferreira.vestigium.domain.ItemId;
import jakarta.validation.Valid;
import java.net.URI;
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

@RestController
@RequestMapping(value = "/items", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ItemController {

  private final ItemRepository repository;

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ItemResponse> create(@Valid @RequestBody CreateItemRequest request) {
    Item item = new Item(parseId(request.id()), request.name(), request.unit());
    repository.insert(item);
    return ResponseEntity.created(URI.create("/items/" + item.id().value()))
        .body(ItemResponse.from(item));
  }

  @GetMapping("/{id}")
  public ItemResponse find(@PathVariable String id) {
    return repository
        .findById(parseId(id))
        .map(ItemResponse::from)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item was not found"));
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
