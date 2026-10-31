package io.github.josepauloferreira.vestigium.infrastructure.persistence;

import io.github.josepauloferreira.vestigium.application.DuplicateItemIdException;
import io.github.josepauloferreira.vestigium.application.ItemRepository;
import io.github.josepauloferreira.vestigium.domain.Item;
import io.github.josepauloferreira.vestigium.domain.ItemId;
import io.github.josepauloferreira.vestigium.domain.PhysicalUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcItemRepository implements ItemRepository {

  private final JdbcClient jdbc;

  public JdbcItemRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(Item item) {
    Objects.requireNonNull(item, "Item is required");
    try {
      jdbc.sql("INSERT INTO items (id, name, unit) VALUES (:id, :name, :unit)")
          .param("id", item.id().value())
          .param("name", item.name())
          .param("unit", item.unit().name())
          .update();
    } catch (DuplicateKeyException exception) {
      throw new DuplicateItemIdException(item.id(), exception);
    }
  }

  @Override
  public Optional<Item> findById(ItemId id) {
    Objects.requireNonNull(id, "Item ID is required");
    return jdbc.sql("SELECT id, name, unit FROM items WHERE id = :id")
        .param("id", id.value())
        .query(
            (row, rowNumber) ->
                new Item(
                    new ItemId(row.getObject("id", UUID.class)),
                    row.getString("name"),
                    PhysicalUnit.valueOf(row.getString("unit"))))
        .optional();
  }
}
