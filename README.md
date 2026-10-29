# Vestigium

An inventory backend project built around a simple question:

> What happened to this item for it to have this stock balance now?

The project originates from a warehouse process where quantities are updated directly in a spreadsheet. Its goal is to preserve stock movements as historical facts and derive balances from that history.

## Current stage

The domain represents items with stable identity and a physical unit, positive fractional movement quantities, and stock entries with an explicit occurrence time and one line per item. `MovementQuantity` stores a `BigDecimal` without rounding or changing its scale. Quantities such as `1.5` and `1.50` have equal numeric meaning and consistent hashes while retaining their original representation.

These rules have unit tests. Stock entries can be constructed in Java; catalog registration, other movement types, balance calculation, an HTTP API and persistence have not been implemented. The JAR contains domain types and is not a runnable application.

## Items and physical units

```java
import io.github.josepauloferreira.vestigium.domain.Item;
import io.github.josepauloferreira.vestigium.domain.ItemId;
import io.github.josepauloferreira.vestigium.domain.PhysicalUnit;
import java.util.UUID;

ItemId id = new ItemId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
Item item = new Item(id, "Synthetic oil", PhysicalUnit.LITRE);
```

`ItemId` is a Value Object record wrapping a required UUID; callers supply the identifier. `Item` is an entity: equality and hashes depend only on its stable identifier. A matching name and unit do not make two different identifiers the same item. Identity equality does not authorize changing an item's unit or converting quantities.

An item requires an identifier, a nonblank name and a physical unit. Missing values throw `NullPointerException`; blank names throw `IllegalArgumentException`. Names retain their supplied whitespace and case. There are no setters, catalog uniqueness rules or rename/unit-change operations.

The initial units are `PAIR`, `UNIT`, `LITRE`, `ROLL` and `KG`. Extend the enum when another physical stock unit is needed. Supplier packaging, unit conversions and whole-number restrictions are not modeled. Independently stocked variants, such as different boot sizes, use separate identifiers.

Lombok generates `Item`'s read-only `id()`, `name()` and `unit()` accessors. Its constructor validation and identity-based equality/hash remain explicit.

## Stock entries

```java
import io.github.josepauloferreira.vestigium.domain.ItemId;
import io.github.josepauloferreira.vestigium.domain.MovementQuantity;
import io.github.josepauloferreira.vestigium.domain.StockEntry;
import io.github.josepauloferreira.vestigium.domain.StockEntryId;
import io.github.josepauloferreira.vestigium.domain.StockEntryLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

ItemId oilId = new ItemId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
ItemId bootsId = new ItemId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

StockEntry entry = new StockEntry(
    new StockEntryId(UUID.fromString("00000000-0000-0000-0000-000000000101")),
    Instant.parse("2026-10-29T13:15:00Z"),
    List.of(
        new StockEntryLine(oilId, MovementQuantity.of(new BigDecimal("1.50"))),
        new StockEntryLine(bootsId, MovementQuantity.of(new BigDecimal("4")))));
```

An entry records materials actually received after tracking begins. It differs from `INITIAL_STOCK`, which will represent known starting inventory in a later increment.

| Type | Meaning |
| --- | --- |
| `StockEntryId` | Value Object record wrapping a required caller-supplied UUID. |
| `StockEntryLine` | Value Object record pairing a required `ItemId` with a valid positive `MovementQuantity`. |
| `StockEntry` | Immutable entity identified by its `StockEntryId`, with a required `occurredAt` and nonempty line list. |

`occurredAt` is an explicitly supplied `Instant`: when the material was received, not when a record was typed. The `Z` in the example denotes UTC. Converting operator-local date/time to an instant and displaying it in a timezone belong to future input/output boundaries. The domain preserves the supplied instant and imposes no current-time, future-date or backdating rule yet.

Each item may occur only once within an entry. Duplicate `ItemId` values throw `IllegalArgumentException`, even when wrapped by different objects or paired with different quantities. The caller supplies the intended total; entries never silently combine or round lines. The same item may appear in separate entries.

Missing required values or a null line throw `NullPointerException`; an empty line list throws `IllegalArgumentException`. `List.copyOf` preserves line order and protects entries against changes to the caller's collection. The exposed list is unmodifiable, and line components are immutable. Lombok generates read-only accessors; constructor validation and identity equality remain explicit.

Equal entry identifiers denote the same entity; identical occurrence/lines with different identifiers represent separate entries. Identity equality does not prevent duplicate storage or establish a correction workflow. A line references `ItemId` without proving catalog existence; catalog validation, units lookup and persistence remain future work. This model does not calculate balances or expose a registration endpoint.

## Movement quantity

```java
import io.github.josepauloferreira.vestigium.domain.MovementQuantity;
import java.math.BigDecimal;

MovementQuantity quantity = MovementQuantity.of(new BigDecimal("0.75"));
```

Positive values such as `1`, `0.75` and `1.234567` are accepted. Zero and negative values throw `IllegalArgumentException`; absent values throw `NullPointerException`. No fixed decimal scale or business rounding policy is defined.

This type represents the magnitude of a movement, not an item's balance. It does not define whether balances can be zero or negative.

`MovementQuantity` is a Java record. Its compact constructor validates both direct construction and the `of(...)` factory; `value()` is generated by Java. Custom `equals()` and `hashCode()` preserve numeric equality across decimal scales. Value Objects default to records in this project, with exceptions justified by concrete requirements.

Lombok remains configured as a compile-time tool for other suitable classes; this record does not use it. It is a `provided` dependency and explicit annotation processor, and is not required at runtime.

## Requirements

| Tool | Version |
| --- | --- |
| JDK | 25 |
| Maven | 3.9.16, provided by the Maven Wrapper |

A global Maven installation is unnecessary. The first build may require network access to download the Maven distribution and build dependencies.

## Select Java 25

Configure your terminal to use an installed JDK 25. On Linux or macOS, replace the example path below with your installation:

```bash
export JAVA_HOME="/absolute/path/to/jdk-25"
export PATH="$JAVA_HOME/bin:$PATH"
java --version
```

These settings apply to the current shell session. If you use an IDE, also select JDK 25 for the project and Maven runner; its terminal may use a different JDK.

## Verify the project

From the repository root:

```bash
./mvnw --version
./mvnw verify
```

Confirm that the Wrapper reports Maven 3.9.16 running on Java 25.

On Windows PowerShell:

```powershell
.\mvnw.cmd verify
```

A successful build finishes with `BUILD SUCCESS` and a test summary with no failures, errors or skipped tests. JUnit Jupiter provides the test framework, AssertJ supplies fluent assertions, and Maven Surefire runs the tests and rejects an empty suite. JUnit and AssertJ are test-scoped dependencies. These tests validate item identity, required attributes, movement quantities and stock entry rules, not a complete inventory workflow. The `verify` phase also checks Java formatting with Spotless.

To run only the tests:

```bash
./mvnw test
```

Coverage includes stock entry construction, duplicate-item rejection, occurrence precision, collection immutability, item identity versus descriptive attributes, required values, blank names, physical units, positive integers/fractions, preservation of scale and precision, rejection of zero/negative/missing quantities, and equality with consistent hash-based lookup. Test reports are written to `target/surefire-reports/`.

## Java formatting

Spotless Maven Plugin 3.10.4 runs google-java-format 1.36.1 in Google style over production and test Java sources. Both versions are pinned in `pom.xml`. It checks Java files only; it does not format the POM, Markdown or generated Maven Wrapper scripts.

Apply formatting before committing:

```bash
./mvnw spotless:apply
```

Check formatting without changing files:

```bash
./mvnw spotless:check
```

`./mvnw verify` includes this check and fails if formatting differs. It never applies formatting automatically. `./mvnw test` runs tests without the formatting check. An IDE formatter is optional; the Maven configuration is the reproducible project standard. See the [Spotless Maven documentation](https://github.com/diffplug/spotless/blob/main/plugin-maven/README.md) and [google-java-format](https://github.com/google/google-java-format).

## JDK validation

The Maven Enforcer Plugin checks the JDK running Maven during the `validate` phase, before compilation. It requires Java 25 and rejects other major versions, even when the project has no source files. If this check fails, select JDK 25 through `JAVA_HOME` and retry.
