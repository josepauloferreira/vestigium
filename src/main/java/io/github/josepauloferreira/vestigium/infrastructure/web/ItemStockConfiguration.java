package io.github.josepauloferreira.vestigium.infrastructure.web;

import io.github.josepauloferreira.vestigium.application.ItemRepository;
import io.github.josepauloferreira.vestigium.application.ItemStockQueries;
import io.github.josepauloferreira.vestigium.application.StockEntryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ItemStockConfiguration {
  @Bean
  ItemStockQueries itemStockQueries(ItemRepository items, StockEntryRepository entries) {
    return new ItemStockQueries(items, entries);
  }
}
