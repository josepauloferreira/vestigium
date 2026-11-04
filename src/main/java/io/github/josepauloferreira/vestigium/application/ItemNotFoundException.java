package io.github.josepauloferreira.vestigium.application;

public class ItemNotFoundException extends RuntimeException {
  public ItemNotFoundException() {
    super("Item was not found");
  }
}
