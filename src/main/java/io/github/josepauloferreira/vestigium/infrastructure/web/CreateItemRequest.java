package io.github.josepauloferreira.vestigium.infrastructure.web;

import io.github.josepauloferreira.vestigium.domain.PhysicalUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateItemRequest(
    @NotNull(message = "id is required") String id,
    @NotBlank(message = "name must not be blank")
        @Pattern(
            regexp = "[\\x{1}-\\x{D7FF}\\x{E000}-\\x{10FFFF}]*",
            message = "name must contain valid Unicode without NUL")
        String name,
    @NotNull(message = "unit is required") PhysicalUnit unit) {}
