package io.github.josepauloferreira.vestigium.infrastructure.web;

import io.github.josepauloferreira.vestigium.domain.PhysicalUnit;
import java.math.BigDecimal;
import java.util.UUID;

public record ItemBalanceResponse(UUID itemId, PhysicalUnit unit, BigDecimal quantity) {}
