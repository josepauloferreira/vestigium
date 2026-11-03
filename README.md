# Vestigium

An inventory backend project built around a simple question:

> What happened to this item for it to have this stock balance now?

The project originates from a warehouse process where quantities are updated directly in a spreadsheet. Its goal is to preserve stock movements as historical facts and derive balances from that history.

## Current stage

The domain represents items with stable identity and a physical unit, positive fractional movement quantities, stock entries with an explicit occurrence time and one line per item, and per-item balances derived from supplied entries. `MovementQuantity` stores a `BigDecimal` without rounding or changing its scale. Quantities such as `1.5` and `1.50` have equal numeric meaning and consistent hashes while retaining their original representation.

These rules have unit tests. A Spring Boot application exposes a basic HTTP health endpoint and runs as an executable JAR. The item catalog and complete stock entries use Spring Data JPA and Hibernate with PostgreSQL, Flyway migrations and real-database integration tests. Entries preserve ordered lines and commit atomically. Business HTTP endpoints and other movement types have not been implemented. Entry-derived totals are not yet complete operational stock balances.

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

An item requires an identifier, a nonblank name and a physical unit. Missing values throw `NullPointerException`; blank names throw `IllegalArgumentException`. Names retain their supplied whitespace and case. There are no setters, name-uniqueness rules or rename/unit-change operations.

The initial units are `PAIR`, `UNIT`, `LITRE`, `ROLL` and `KG`. Extend the enum when another physical stock unit is needed. Supplier packaging, unit conversions and whole-number restrictions are not modeled. Independently stocked variants, such as different boot sizes, use separate identifiers.

Lombok generates `Item`'s read-only `id()`, `name()` and `unit()` accessors. Its constructor validation and identity-based equality/hash remain explicit.

## Persistent item catalog

`ItemRepository` exposes only `insert(Item)` and `findById(ItemId)`. Its Spring-managed `JpaItemRepository` implementation delegates to a small Spring Data JPA repository backed by Hibernate. A separate `ItemEntity` maps the table; the immutable domain `Item` keeps its existing validation and has no JPA annotations. Callers supply the UUID; persistence does not replace it or normalize the name.

`insert` creates a row. Reusing an existing ID throws `DuplicateItemIdException` and leaves the original name and unit unchanged. There is no update/upsert behavior. Different IDs may share a name. `findById` returns `Optional.empty()` for an unknown ID, otherwise reconstructing a validated domain item with its stored attributes.

Because UUIDs are assigned before storage, `ItemEntity` implements Spring Data's `Persistable` contract to identify new instances explicitly. Persistence/load callbacks mark them as existing afterward. This makes a new insertion use JPA `persist` instead of selecting `merge` just because its ID is populated. `saveAndFlush` executes the insert within the repository transaction so an identity conflict is reported to the caller. The adapter distinguishes identity conflicts from other integrity failures.

PostgreSQL text cannot store the NUL character. Such input fails as a data-integrity error; it is not silently changed or reported as a duplicate identity.

The first Flyway migration, `V1__create_items.sql`, creates `items` with a UUID primary key, a required text name and a required physical-unit name checked against the current five values. Java validates item construction, including blank names; the migration does not attempt to reproduce Java's Unicode blank-string rules. Adding a new physical unit will also require a migration of the database constraint. There is no name-uniqueness constraint or arbitrary name-length rule.

Flyway applies versioned migrations on application startup. It is the only schema creation/change mechanism; basic `schema.sql`/`data.sql` initialization is disabled and Hibernate uses `ddl-auto: validate`. Hibernate checks the mapping against the migrated schema without generating tables. Open EntityManager in View is disabled; repository operations use their transaction boundaries. Keep applied migrations unchanged and introduce a new version for later schema changes.

Migrations define database constraints and physical column types. Persistence entities keep the mapping needed by Hibernate, including identity and string-enum representation, without repeating SQL constraints or DDL fragments. Domain constructors still validate business objects. Integration tests also bypass JPA to verify that PostgreSQL rejects null names, null units and unsupported units, and verify that long names survive the JPA round trip.

The repository is exercised by integration tests; no item registration/query HTTP routes exist yet. Item equality compares only identity, so persistence tests explicitly compare ID, name and unit to verify the mapping.

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

`occurredAt` is an explicitly supplied `Instant`: when the material was received, not when a record was typed. It must be exactly representable in microseconds: `getNano() % 1_000 == 0`. More precise values throw `IllegalArgumentException`; they are never rounded or truncated. The `Z` in the example denotes UTC. Converting operator-local date/time to an instant and displaying it in a timezone belong to future input/output boundaries. Accepted instants are preserved exactly; no current-time, future-date or backdating rule is imposed.

Each item may occur only once within an entry. Duplicate `ItemId` values throw `IllegalArgumentException`, even when wrapped by different objects or paired with different quantities. The caller supplies the intended total; entries never silently combine or round lines. The same item may appear in separate entries.

Missing required values or a null line throw `NullPointerException`; an empty line list throws `IllegalArgumentException`. `List.copyOf` preserves line order and protects entries against changes to the caller's collection. The exposed list is unmodifiable, and line components are immutable. Lombok generates read-only accessors; constructor validation and identity equality remain explicit.

Equal entry identifiers denote the same entity; identical occurrence/lines with different identifiers represent separate entries. A domain line references `ItemId` without performing database access. During persistence, foreign keys require every referenced item to exist in the catalog. Entry objects preserve receipt facts; StockBalanceCalculator derives totals separately. There is no registration endpoint or correction workflow.

### Durable stock entries

`StockEntryRepository` exposes `insert(StockEntry)` and `findById(StockEntryId)`. Insertion is one transaction for the header and every line; failure rolls back all of that entry's writes. A repeated identity throws `DuplicateStockEntryIdException` without changing stored facts, even for an identical submission. An unknown identity returns `Optional.empty()`; an unknown item reference fails with a data-integrity error. Existing outer transactions are joined, so the caller still controls their final commit/rollback.

Flyway V2 creates `stock_entries` and `stock_entry_lines`. The header stores the supplied UUID and a finite `TIMESTAMP(6) WITH TIME ZONE`. Lines have an explicit zero-based position, an item foreign key and a positive finite quantity. Primary/unique constraints prevent repeated positions or items within an entry. The domain requires at least one line, and the application inserts the complete entry atomically. V1 stays unchanged; V2 upgrades an existing item catalog.

`StockEntryEntity` maps its owned line values using `@ElementCollection`, `@CollectionTable` and `@OrderColumn`. These lines have no separate entity identity or repository. A separate embeddable persistence record maps them while the domain `StockEntryLine` remains independent of JPA. Read operations reconstruct the domain inside a read-only transaction so lazy collection loading does not depend on an open web request.

Quantity storage uses unconstrained PostgreSQL `NUMERIC`, with a separate original `quantity_scale`. When needed to fit the fractional storage limit, the persistence mapping removes trailing zeroes from the stored magnitude. It reconstructs the original scale with `RoundingMode.UNNECESSARY`, preserving values such as `1.50` and `1E+3`. Before binding JDBC values, it rejects magnitudes outside PostgreSQL's physical limits: more than 131,072 integer digits or more than 16,383 necessary fractional digits. This is a storage boundary, not a new domain decimal policy. Unsupported quantities and dates outside the database range fail instead of becoming rounded values or infinity. See [PostgreSQL numeric types](https://www.postgresql.org/docs/18/datatype-numeric.html) and [date/time types](https://www.postgresql.org/docs/18/datatype-datetime.html).

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

## Balances derived from entries

Using `entry` and `oilId` from the stock-entry example above:

```java
import io.github.josepauloferreira.vestigium.domain.StockBalanceCalculator;

StockEntry anotherEntry = new StockEntry(
    new StockEntryId(UUID.fromString("00000000-0000-0000-0000-000000000102")),
    Instant.parse("2026-10-29T14:30:00Z"),
    List.of(new StockEntryLine(oilId, MovementQuantity.of(new BigDecimal("0.75")))));

StockBalanceCalculator calculator = new StockBalanceCalculator();
BigDecimal oilBalance = calculator.calculate(oilId, List.of(entry, anotherEntry));
// Numeric result: 2.25
```

`StockBalanceCalculator` is a stateless domain service. It selects lines by `ItemId` value equality and sums their quantities using `BigDecimal` without rounding, a fixed display scale or a stored balance field. The numeric result is independent of entry order. Input entries, line order and quantity representations remain unchanged; repeated calls retain no previous total or identifiers.

An empty history or a history without the requested item returns numeric zero. This describes the supplied facts, not a confirmed physical count or known starting inventory. The calculator does not verify catalog existence or convert units.

The complete history must contain each `StockEntryId` at most once. Repeated identities throw `IllegalArgumentException`, even when their contents differ or they contain no matching item. Different entry identifiers represent different receipts and both contribute, even with identical contents. Missing item identity, history or an entry element throws `NullPointerException`. This input validation does not implement global storage uniqueness or HTTP idempotency.

Every supplied entry participates regardless of `occurredAt`. This is a total over explicit input, without an implicit current-time cutoff or historical-period query. `INITIAL_STOCK`, exits, returns and adjustments remain future increments, as do negative-stock and chronology policies.

The first matching quantity is used directly before adding subsequent quantities. This avoids an unnecessary zero addition that cannot align every valid extreme `BigDecimal` scale; a regression test covers the minimum supported scale.

## Requirements

| Tool | Version |
| --- | --- |
| JDK | 25 |
| Maven | 3.9.16, provided by the Maven Wrapper |
| Spring Boot | 4.1.2, managed by the Maven parent |
| PostgreSQL | 18.6, supplied by the pinned official container image |
| Docker | A running Docker-compatible environment supported by Testcontainers, for the full test suite |
| Docker Compose | For the local development database |

A global Maven or PostgreSQL installation is unnecessary. The first build may require network access to download the Maven distribution, dependencies and container images. Database integration tests use disposable PostgreSQL containers, separate from the local development database; they do not require a free host port 5432.

## Select Java 25

Configure your terminal to use an installed JDK 25. On Linux or macOS, replace the example path below with your installation:

```bash
export JAVA_HOME="/absolute/path/to/jdk-25"
export PATH="$JAVA_HOME/bin:$PATH"
java --version
```

These settings apply to the current shell session. If you use an IDE, also select JDK 25 for the project and Maven runner; its terminal may use a different JDK.

## Run the application

From the repository root, create your local configuration once:

```bash
cp .env.example .env
```

Edit `.env` as needed. Git ignores `.env` and local variants such as `.env.local`; `.env.example` is the versioned template. Only the root `.env` is loaded automatically. Compose reads it for interpolation, and Spring Boot imports it as an optional properties file. Use unquoted `KEY=value` entries without shell `export` statements. Variables already exported in your shell take precedence. Run Maven and the JAR from the repository root so both tools find the same file.

| Variable | Local default | Purpose |
| --- | --- | --- |
| `DB_PORT` | `5432` | Published PostgreSQL port and the application's default JDBC URL port. |
| `DB_USERNAME` | `vestigium` | PostgreSQL initialization user and application database user. |
| `DB_PASSWORD` | `vestigium-local` | PostgreSQL initialization password and application database password. |
| `SERVER_PORT` | `8080` | Application HTTP port. |
| `DB_URL` | Derived from `DB_PORT` | Optional full JDBC URL override; does not reconfigure Compose. |

The example contains local demonstration values. The optional import also allows CI and Testcontainers to run without `.env`; service connections supply their own test database details. See [Spring Boot configuration imports](https://docs.spring.io/spring-boot/reference/features/external-config.html#importing-extensionless-files) and [Compose environment interpolation](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/).

Start the local database:

```bash
docker compose up -d --wait
```

Compose exposes PostgreSQL on `127.0.0.1:5432` and keeps its data in a named volume. The PostgreSQL 18 image mounts that volume at `/var/lib/postgresql`. The image's patch version and digest are pinned in both Compose and the test configuration.

The local defaults are database `vestigium`, user `vestigium` and development password `vestigium-local`. `DB_USERNAME` and `DB_PASSWORD` override the corresponding Compose/application values. `DB_URL` overrides the application's JDBC URL. These defaults are for the local demonstration. PostgreSQL initialization variables apply only to an empty data directory; changing them does not rewrite credentials in an existing volume. See the [official image documentation](https://hub.docker.com/_/postgres).

With JDK 25 selected, start the application:

```bash
./mvnw spring-boot:run
```

The application listens on `127.0.0.1:8080` by default. From another terminal:

```bash
curl --fail --include --header 'Accept: application/json' http://127.0.0.1:8080/actuator/health
```

Expect HTTP `200` and the JSON body:

```json
{"status":"UP"}
```

Spring Boot Actuator supplies this endpoint; there is no custom health controller. Only `health` is exposed over HTTP, without component details. Separate liveness/readiness probe groups remain disabled. The configured checks now include the database connection. Normal application startup requires the configured database and successful Flyway initialization; `UP` does not establish a complete inventory workflow.

Stop the process with **Ctrl+C**. To use another port on Linux or macOS:

```bash
SERVER_PORT=8081 ./mvnw spring-boot:run
```

Then use port `8081` in the request. The loopback address is the default for this local demonstration; Spring Boot's standard external configuration can override the address and port. Public deployment and authentication are separate future work.

If database port 5432 is occupied, set `DB_PORT=5433` in `.env`. Both Compose and the application's default JDBC URL use that value. Alternatively, supply it for each command:

```bash
DB_PORT=5433 docker compose up -d --wait
DB_PORT=5433 ./mvnw spring-boot:run
```

To build and run the executable JAR:

```bash
./mvnw verify
java -jar target/vestigium-0.0.1-SNAPSHOT.jar
```

Use the same health request and **Ctrl+C** to stop. An alternate JAR port can be supplied with `--server.port=8081`. The Spring Boot Maven plugin packages the embedded server and runtime dependencies; Lombok is explicitly excluded. The `.jar.original` file is the intermediate archive, not the executable application.

After stopping the application, stop the local database with:

```bash
docker compose stop postgres
```

Run `docker compose up -d --wait` to start it again. The development volume is preserved. `docker compose down` also keeps named volumes unless volume removal is explicitly requested; do not remove the volume when you intend to keep the catalog. Testcontainers manages and removes its own disposable test resources independently.

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

A successful build finishes with `BUILD SUCCESS` and a test summary with no failures, errors or skipped tests. JUnit Jupiter provides the test framework, AssertJ supplies fluent assertions, and Maven Surefire runs the tests and rejects an empty suite. The test starter supplies these libraries in test scope; the parent manages AssertJ 3.27.7, and `junit-jupiter.version` retains the aligned JUnit 6.1.3 family. Existing compiler, Surefire, Enforcer and formatter configuration remains explicit. The `verify` phase also creates the executable JAR and checks Java formatting with Spotless.

Domain tests validate item identity, required attributes, movement quantities, stock entry rules and per-item totals from entries. `JpaItemRepositoryTest` uses real Hibernate/PostgreSQL and the actual Flyway migration to verify round trips, all physical units, preserved names, missing identities, duplicate-ID rejection (including identical content and an identity already loaded in a transaction), shared names and committed data across closed/reopened application contexts. These tests do not rely on rollback-only transactions or an in-memory database.

`JpaStockEntryRepositoryTest` verifies complete ordered entries, exact occurrence times, decimal values/scales (including storage boundaries), duplicate identity handling, foreign keys, database constraints, committed data across closed/reopened contexts and V1-to-V2 upgrades with existing items. A test-only trigger deliberately fails a later line after observing the header and first line in the same transaction. A nontransactional sequence records that observation; separate reads then prove the failed entry left no rows. The trigger/function/sequence are removed from the disposable test database after the test and are never installed by application migrations.

`ApplicationHttpTest` starts a real embedded server on an available port with a Testcontainers database, checks the health response and verifies that `/actuator/env` and `/actuator/info` are not exposed. HTTP clients, application contexts and test databases are closed after use. Full verification requires a working Docker environment; database tests are not silently skipped when it is missing. These checks do not establish a complete inventory workflow.

To run the full test suite, including database and HTTP integration tests:

```bash
./mvnw test
```

To run only the domain tests without Docker:

```bash
./mvnw -Dtest='**/domain/*Test' test
```

Coverage includes exact per-item entry totals, empty histories, duplicate-entry rejection, preserved inputs, stateless repeated calls, stock entry construction, duplicate-item rejection, occurrence precision, collection immutability, item identity versus descriptive attributes, required values, blank names, physical units, positive integers/fractions, preservation of scale and precision, rejection of zero/negative/missing quantities, and equality with consistent hash-based lookup. Test reports are written to `target/surefire-reports/`.

## Continuous integration

The [CI workflow](.github/workflows/verify.yml) defines one `Verify` job on a GitHub-hosted Ubuntu 24.04 runner. It runs when a pull request targeting `main` is opened, reopened or updated, and when commits are pushed to `main`, including merges.

The workflow checks out the candidate, selects Temurin using the tracked `.java-version`, caches Maven dependencies, prints the Java/Maven versions and runs:

```bash
./mvnw -B --no-transfer-progress verify
```

The Wrapper selects Maven 3.9.16. This is the same `verify` gate used locally: compilation, tests, JAR packaging and Spotless checks. Batch mode and suppressed transfer progress keep CI non-interactive and its logs readable. The workflow checks formatting without rewriting source files.

For pull requests, the default checkout tests GitHub's temporary merge candidate with the base branch. A push to `main` checks the resulting main commit. Inspect the relevant commit and run together when reviewing results.

Open the PR's **Checks** section or the repository's [Actions tab](https://github.com/josepauloferreira/vestigium/actions), select **CI**, then **Verify** to inspect the setup, tool versions and Maven output. A failed test, compilation or formatting check fails the job. A local successful build or a workflow file alone does not establish that a GitHub run passed.

Official actions are pinned to verified release commits, with version comments beside their references. The workflow uses read-only repository contents permission, does not persist checkout credentials and requires no custom secrets. The dependency cache is an optimization; a cold cache still downloads the dependencies needed by the build.

Adding this workflow does not configure required checks or branch protection. Those repository settings are separate from displaying a CI result. This first workflow covers Linux/JDK 25; it does not establish Windows or macOS execution evidence, deployment or package publication.

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
