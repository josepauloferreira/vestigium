package io.github.josepauloferreira.vestigium.infrastructure.web.initialstock;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateInitialStockRequest(
    @NotNull(message = "quantity is required")
        @DecimalMin(value = "0", message = "quantity must not be negative")
        BigDecimal quantity,
    @NotBlank(message = "occurredAt is required") String occurredAt) {}
