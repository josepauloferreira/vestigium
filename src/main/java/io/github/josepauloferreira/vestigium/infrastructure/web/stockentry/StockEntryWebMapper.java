package io.github.josepauloferreira.vestigium.infrastructure.web.stockentry;

import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntry;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntryLine;
import io.github.josepauloferreira.vestigium.infrastructure.web.shared.WebMappingConfiguration;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = WebMappingConfiguration.class)
public interface StockEntryWebMapper {
  @Mapping(target = "id", expression = "java(entry.id().value())")
  @Mapping(target = "occurredAt", expression = "java(entry.occurredAt().toString())")
  @Mapping(target = "lines", expression = "java(toLines(entry.lines()))")
  StockEntryResponse toResponse(StockEntry entry);

  List<StockEntryResponse.Line> toLines(List<StockEntryLine> lines);

  @Mapping(target = "itemId", source = "itemId.value")
  @Mapping(target = "quantity", source = "quantity.value")
  StockEntryResponse.Line toLine(StockEntryLine line);
}
