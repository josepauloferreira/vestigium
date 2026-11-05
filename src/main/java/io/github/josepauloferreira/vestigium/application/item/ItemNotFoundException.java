package io.github.josepauloferreira.vestigium.application.item;

public class ItemNotFoundException extends RuntimeException {
  public ItemNotFoundException() {
    super("Item was not found");
  }
}
