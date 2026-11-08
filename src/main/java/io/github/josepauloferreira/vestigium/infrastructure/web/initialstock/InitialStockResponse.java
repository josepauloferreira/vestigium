package io.github.josepauloferreira.vestigium.infrastructure.web.initialstock;

import io.github.josepauloferreira.vestigium.domain.item.PhysicalUnit;
import java.math.BigDecimal;
import java.util.UUID;

public record InitialStockResponse(
    UUID itemId, PhysicalUnit unit, BigDecimal quantity, String occurredAt) {}
