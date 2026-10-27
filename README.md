# Vestigium

An inventory backend project built around a simple question:

> What happened to this item for it to have this stock balance now?

The project originates from a warehouse process where quantities are updated directly in a spreadsheet. Its goal is to preserve stock movements as historical facts and derive balances from that history.

## Current stage

Vestigium is at the project-bootstrap stage. The repository currently provides a Maven build and Maven Wrapper. Application code, inventory rules, business tests, an HTTP API and persistence have not been implemented.

The generated JAR is currently empty and is not a runnable application.

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

A successful build finishes with `BUILD SUCCESS`. At this stage, `No sources to compile`, `No tests to run` and the empty-JAR warning are expected. Success validates the build setup, not inventory behavior.

## JDK validation

The Maven Enforcer Plugin checks the JDK running Maven during the `validate` phase, before compilation. It requires Java 25 and rejects other major versions, even when the project has no source files. If this check fails, select JDK 25 through `JAVA_HOME` and retry.
