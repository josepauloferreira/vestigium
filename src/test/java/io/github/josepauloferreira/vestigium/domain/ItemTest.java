package io.github.josepauloferreira.vestigium.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ItemTest {
  private static final ItemId ID =
      new ItemId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

  @ParameterizedTest
  @EnumSource(PhysicalUnit.class)
  void representsAnItemInEachSupportedPhysicalUnit(PhysicalUnit unit) {
    Item item = new Item(ID, "Synthetic material", unit);

    assertThat(item.id()).isEqualTo(ID);
    assertThat(item.name()).isEqualTo("Synthetic material");
    assertThat(item.unit()).isEqualTo(unit);
  }

  @ParameterizedTest
  @ValueSource(strings = {"  Copper cable  ", "Copper   cable", "Safety BOOT 42"})
  void preservesTheSuppliedName(String name) {
    Item item = new Item(ID, name, PhysicalUnit.UNIT);

    assertThat(item.name()).isEqualTo(name);
  }

  @Test
  void rejectsMissingIdentity() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Item(null, "Copper cable", PhysicalUnit.ROLL))
        .withMessage("Item ID is required");
  }

  @Test
  void rejectsMissingName() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Item(ID, null, PhysicalUnit.ROLL))
        .withMessage("Item name is required");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t", "\n", " \t\n", "\u2003"})
  void rejectsBlankNames(String name) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Item(ID, name, PhysicalUnit.ROLL))
        .withMessage("Item name must not be blank");
  }

  @Test
  void rejectsMissingPhysicalUnit() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Item(ID, "Copper cable", null))
        .withMessage("Physical unit is required");
  }

  @Test
  void identifiesTheSameItemDespiteDifferentDescriptiveAttributes() {
    Item first = new Item(ID, "Copper cable", PhysicalUnit.ROLL);
    Item second = new Item(new ItemId(ID.value()), "Cable", PhysicalUnit.UNIT);

    assertThat(first).isEqualTo(second);
    assertThat(second).isEqualTo(first);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
    assertThat(Set.of(first).contains(second)).isTrue();
  }

  @Test
  void distinguishesItemsWithTheSameNameAndUnitButDifferentIdentities() {
    Item first = new Item(ID, "Safety boots", PhysicalUnit.PAIR);
    ItemId anotherId = new ItemId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    Item second = new Item(anotherId, "Safety boots", PhysicalUnit.PAIR);

    assertThat(first).isNotEqualTo(second);
    assertThat(second).isNotEqualTo(first);
  }

  @Test
  void equalsItselfButNotNullOrOtherTypes() {
    Item item = new Item(ID, "Safety boots", PhysicalUnit.PAIR);

    assertThat(item).isEqualTo(item).isNotEqualTo(null).isNotEqualTo(ID);
  }
}
