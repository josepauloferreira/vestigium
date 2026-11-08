package io.github.josepauloferreira.vestigium.infrastructure.web.balance;

import io.github.josepauloferreira.vestigium.domain.item.PhysicalUnit;
import java.math.BigDecimal;
import java.util.UUID;

public record ItemBalanceResponse(UUID itemId, PhysicalUnit unit, BigDecimal quantity) {}
