# Vestigium

An inventory backend project built around a simple question:

> What happened to this item for it to have this stock balance now?

The project originates from a warehouse process where quantities are updated directly in a spreadsheet. Its goal is to preserve stock movements as historical facts and derive balances from that history.

## Current stage

This branch introduces the first domain rule: a movement quantity must be present and strictly positive, and may be fractional. `MovementQuantity` stores a `BigDecimal` without rounding or changing its scale. Quantities such as `1.5` and `1.50` have equal numeric meaning and consistent hashes while retaining their original representation.

The rule has unit tests. Item registration, complete stock movements, balance calculation, an HTTP API and persistence have not been implemented. The JAR contains the domain class and is not a runnable application.

## Movement quantity

```java
import io.github.josepauloferreira.vestigium.domain.MovementQuantity;
import java.math.BigDecimal;

MovementQuantity quantity = MovementQuantity.of(new BigDecimal("0.75"));
```

Positive values such as `1`, `0.75` and `1.234567` are accepted. Zero and negative values throw `IllegalArgumentException`; absent values throw `NullPointerException`. No fixed decimal scale or business rounding policy is defined.

This type represents the magnitude of a movement, not an item's balance. It does not define whether balances can be zero or negative.

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

A successful build finishes with `BUILD SUCCESS` and a test summary with no failures, errors or skipped tests. JUnit Jupiter provides the behavioral tests; Maven Surefire runs them and rejects an empty test suite. These tests validate movement quantities, not a complete inventory workflow.

To run only the tests:

```bash
./mvnw test
```

Coverage includes positive integers/fractions, preservation of scale and precision, rejection of zero/negative/missing values, and numeric equality across scales with consistent hash-based lookup. Test reports are written to `target/surefire-reports/`.

## JDK validation

The Maven Enforcer Plugin checks the JDK running Maven during the `validate` phase, before compilation. It requires Java 25 and rejects other major versions, even when the project has no source files. If this check fails, select JDK 25 through `JAVA_HOME` and retry.
