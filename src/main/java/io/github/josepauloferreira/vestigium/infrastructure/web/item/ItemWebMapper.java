package io.github.josepauloferreira.vestigium.infrastructure.web.item;

import io.github.josepauloferreira.vestigium.domain.item.Item;
import io.github.josepauloferreira.vestigium.infrastructure.web.shared.WebMappingConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = WebMappingConfiguration.class)
public interface ItemWebMapper {
  @Mapping(target = "id", expression = "java(item.id().value())")
  @Mapping(target = "name", expression = "java(item.name())")
  @Mapping(target = "unit", expression = "java(item.unit())")
  ItemResponse toResponse(Item item);
}
