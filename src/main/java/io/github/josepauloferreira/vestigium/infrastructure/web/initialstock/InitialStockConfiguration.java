package io.github.josepauloferreira.vestigium.infrastructure.web.initialstock;

import io.github.josepauloferreira.vestigium.application.initialstock.InitialStockOperations;
import io.github.josepauloferreira.vestigium.application.initialstock.InitialStockRepository;
import io.github.josepauloferreira.vestigium.application.item.ItemRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class InitialStockConfiguration {
  @Bean
  InitialStockOperations initialStockOperations(
      ItemRepository items, InitialStockRepository stocks) {
    return new InitialStockOperations(items, stocks);
  }
}
