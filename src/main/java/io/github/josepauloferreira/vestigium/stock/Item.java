package io.github.josepauloferreira.vestigium.stock;

import java.util.Objects;

public record Item(String name) {

  public Item {
    Objects.requireNonNull(name, "name");
  }
}
