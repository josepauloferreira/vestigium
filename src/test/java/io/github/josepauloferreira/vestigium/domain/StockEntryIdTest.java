package io.github.josepauloferreira.vestigium.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockEntryIdTest {
  private static final UUID VALUE = UUID.fromString("00000000-0000-0000-0000-000000000101");

  @Test
  void preservesTheSuppliedIdentifier() {
    StockEntryId id = new StockEntryId(VALUE);

    assertThat(id.value()).isEqualTo(VALUE);
    assertThat(new StockEntryId(id.value())).isEqualTo(id);
  }

  @Test
  void rejectsMissingIdentifiers() {
    assertThatNullPointerException()
        .isThrownBy(() -> new StockEntryId(null))
        .withMessage("Stock entry ID is required");
  }

  @Test
  void treatsMatchingIdentifiersAsTheSameValue() {
    StockEntryId first = new StockEntryId(VALUE);
    StockEntryId second = new StockEntryId(UUID.fromString(VALUE.toString()));

    assertThat(first).isEqualTo(second);
    assertThat(second).isEqualTo(first);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
    assertThat(Set.of(first).contains(second)).isTrue();
  }

  @Test
  void distinguishesDifferentIdentifiersAndIdentifierTypes() {
    StockEntryId id = new StockEntryId(VALUE);
    StockEntryId anotherId =
        new StockEntryId(UUID.fromString("00000000-0000-0000-0000-000000000102"));

    assertThat(id).isNotEqualTo(anotherId).isNotEqualTo(new ItemId(VALUE));
  }
}
