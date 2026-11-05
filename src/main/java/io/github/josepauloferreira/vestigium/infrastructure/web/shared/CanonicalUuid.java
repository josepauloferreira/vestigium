package io.github.josepauloferreira.vestigium.infrastructure.web.shared;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The canonical UUID contract shared by HTTP parsing, validation and OpenAPI. */
public final class CanonicalUuid {
  public static final String PATTERN =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  private CanonicalUuid() {}

  public static UUID parse(String value, String field) {
    try {
      UUID id = UUID.fromString(value);
      if (!id.toString().equalsIgnoreCase(value))
        throw new IllegalArgumentException("Noncanonical UUID");
      return id;
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, field + " must be a canonical UUID");
    }
  }
}
