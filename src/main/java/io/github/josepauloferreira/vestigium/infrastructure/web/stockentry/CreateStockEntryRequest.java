package io.github.josepauloferreira.vestigium.infrastructure.web.stockentry;

import io.github.josepauloferreira.vestigium.infrastructure.web.shared.CanonicalUuid;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;

public record CreateStockEntryRequest(
    @NotNull(message = "id is required")
        @Pattern(regexp = CanonicalUuid.PATTERN, message = "id must be a canonical UUID")
        String id,
    @NotBlank(message = "occurredAt is required") String occurredAt,
    @NotEmpty(message = "lines must contain at least one line")
        List<@NotNull(message = "line is required") @Valid Line> lines) {

  @Schema(name = "StockEntryRequestLine")
  public record Line(
      @NotNull(message = "itemId is required")
          @Pattern(regexp = CanonicalUuid.PATTERN, message = "itemId must be a canonical UUID")
          String itemId,
      @NotNull(message = "quantity is required")
          @DecimalMin(
              value = "0",
              inclusive = false,
              message = "quantity must be greater than zero")
          BigDecimal quantity) {}
}
