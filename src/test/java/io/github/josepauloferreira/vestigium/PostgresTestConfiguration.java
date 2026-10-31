package io.github.josepauloferreira.vestigium;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    // PostgreSQL 18.6 on Bookworm; the same image digest is used by Compose.
    return new PostgreSQLContainer(
        "postgres@sha256:5c4783a8cc3f0074912164f94dfa1be4e043438f0f707cf968d7af9a0d31a2c8");
  }
}
