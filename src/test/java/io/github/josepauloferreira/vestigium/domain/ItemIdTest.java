package io.github.josepauloferreira.vestigium.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItemIdTest {
  private static final UUID VALUE = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Test
  void preservesTheSuppliedIdentifier() {
    ItemId id = new ItemId(VALUE);

    assertThat(id.value()).isEqualTo(VALUE);
    assertThat(new ItemId(id.value())).isEqualTo(id);
  }

  @Test
  void rejectsMissingIdentifiers() {
    assertThatNullPointerException()
        .isThrownBy(() -> new ItemId(null))
        .withMessage("Item ID is required");
  }

  @Test
  void treatsMatchingIdentifiersAsTheSameValue() {
    ItemId first = new ItemId(VALUE);
    ItemId second = new ItemId(UUID.fromString(VALUE.toString()));

    assertThat(first).isEqualTo(second);
    assertThat(second).isEqualTo(first);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
    assertThat(Set.of(first).contains(second)).isTrue();
  }

  @Test
  void distinguishesDifferentIdentifiers() {
    ItemId first = new ItemId(VALUE);
    ItemId second = new ItemId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    assertThat(first).isNotEqualTo(second);
  }
}
