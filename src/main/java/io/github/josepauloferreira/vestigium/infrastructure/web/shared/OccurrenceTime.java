package io.github.josepauloferreira.vestigium.infrastructure.web.shared;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Parses the explicit-offset occurrence format shared by stock HTTP requests. */
public final class OccurrenceTime {
  private OccurrenceTime() {}

  public static Instant parse(String value) {
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (DateTimeParseException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "occurredAt must be an ISO 8601 date/time with Z or an explicit offset");
    }
  }
}
