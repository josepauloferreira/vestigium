package io.github.josepauloferreira.vestigium.infrastructure.web.initialstock;

import io.github.josepauloferreira.vestigium.application.initialstock.InitialStockOperations;
import io.github.josepauloferreira.vestigium.infrastructure.web.shared.WebMappingConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = WebMappingConfiguration.class)
public interface InitialStockWebMapper {
  @Mapping(target = "itemId", expression = "java(snapshot.stock().itemId().value())")
  @Mapping(target = "quantity", expression = "java(snapshot.stock().quantity().value())")
  @Mapping(target = "occurredAt", expression = "java(snapshot.stock().occurredAt().toString())")
  InitialStockResponse toResponse(InitialStockOperations.Snapshot snapshot);
}
