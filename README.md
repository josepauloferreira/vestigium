# Vestigium

Vestigium is an inventory backend built around historical stock movements.

The project originates from a real warehouse process where stock balances are updated directly in a spreadsheet, making it difficult to explain how the current quantity was reached.

Vestigium preserves stock changes as historical facts so the current balance remains traceable.

## Requirements

- Java 25
- Git

A global Maven installation is not required. The project uses the Maven Wrapper.

## GitHub Codespaces

The repository includes a Dev Container with the expected Java environment.

```bash
java --version
./mvnw --version
./mvnw verify
```

## Local development

```bash
git clone https://github.com/josepauloferreira/vestigium.git
cd vestigium
```

Use JDK 25 and open the project in IntelliJ IDEA as a Maven project.

## Validation

```bash
./mvnw verify
```

A successful validation finishes with `BUILD SUCCESS`.

## Why Vestigium?

*Vestigium* is Latin for a trace or footprint.

The name reflects the core idea of the project: every stock change should leave a trace that helps explain the current state.
