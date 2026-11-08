package io.github.josepauloferreira.vestigium.infrastructure.web.balance;

import io.github.josepauloferreira.vestigium.application.balance.ItemStockQueries;
import io.github.josepauloferreira.vestigium.infrastructure.web.shared.WebMappingConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = WebMappingConfiguration.class)
public interface ItemStockWebMapper {
  @Mapping(target = "itemId", source = "itemId.value")
  ItemBalanceResponse toResponse(ItemStockQueries.Balance balance);

  @Mapping(target = "itemId", source = "itemId.value")
  ItemEntryHistoryResponse toResponse(ItemStockQueries.History history);

  @Mapping(target = "entryId", source = "entryId.value")
  @Mapping(target = "occurredAt", expression = "java(receipt.occurredAt().toString())")
  ItemEntryHistoryResponse.Entry toEntry(ItemStockQueries.Receipt receipt);
}
