package io.github.josepauloferreira.vestigium.infrastructure.web.balance;

import io.github.josepauloferreira.vestigium.application.balance.ItemStockQueries;
import io.github.josepauloferreira.vestigium.application.initialstock.InitialStockRepository;
import io.github.josepauloferreira.vestigium.application.item.ItemRepository;
import io.github.josepauloferreira.vestigium.application.stockentry.StockEntryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ItemStockConfiguration {
  @Bean
  ItemStockQueries itemStockQueries(
      ItemRepository items, StockEntryRepository entries, InitialStockRepository initialStocks) {
    return new ItemStockQueries(items, entries, initialStocks);
  }
}
