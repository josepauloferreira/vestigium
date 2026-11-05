# Vestigium

An inventory backend project built around a simple question:

> What happened to this item for it to have this stock balance now?

The project originates from a warehouse process where quantities are updated directly in a spreadsheet. Its goal is to preserve stock movements as historical facts and derive balances from that history.

## Current stage

The domain represents items with stable identity and a physical unit, positive fractional movement quantities, stock entries with an explicit occurrence time and one line per item, and per-item balances derived from supplied entries. `MovementQuantity` stores a `BigDecimal` without rounding or changing its scale. Quantities such as `1.5` and `1.50` have equal numeric meaning and consistent hashes while retaining their original representation.

These rules have unit tests. A Spring Boot application exposes item and stock-entry registration/lookup and basic health over HTTP and runs as an executable JAR. Initial stock can be recorded once per item, including an explicitly counted zero. Balance queries derive quantities from initial stock plus persisted receipts; receipt history remains separately available. OpenAPI and Swagger UI document the business API. The item catalog and complete stock entries use Spring Data JPA and Hibernate with PostgreSQL, Flyway migrations and real-database integration tests. Entries preserve ordered lines and commit atomically. Other movement types have not been implemented. Exits, returns, adjustments and corrections remain future work, so these totals are not yet complete operational stock balances.

## Items and physical units

```java
import io.github.josepauloferreira.vestigium.domain.item.Item;
import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import io.github.josepauloferreira.vestigium.domain.item.PhysicalUnit;
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

The repository and item HTTP routes are exercised by integration tests. Item equality compares only identity, so tests explicitly compare ID, name and unit to verify the complete representation.

## Item HTTP API

Start the database and application as described under [Run the application](#run-the-application). Register a synthetic item:

```bash
curl --include --header 'Content-Type: application/json' \
  --header 'Accept: application/json' \
  --data '{"id":"00000000-0000-0000-0000-000000000201","name":"Synthetic hydraulic oil","unit":"LITRE"}' \
  http://127.0.0.1:8080/items
```

Expect `201 Created`, `Location: /items/00000000-0000-0000-0000-000000000201` and the stored `id`, `name` and `unit` as JSON. The caller supplies a canonical UUID in its full hyphenated form; uppercase hexadecimal is accepted. A repeated ID returns `409 Conflict` even with identical contents. Use a fresh UUID for a new item; different IDs may share a name.

Query the created item:

```bash
curl --include --header 'Accept: application/json' \
  http://127.0.0.1:8080/items/00000000-0000-0000-0000-000000000201
```

Expect `200 OK` with the same attributes, including after application restart. Successful responses use `application/json` and contain only `id`, `name` and `unit`.

| Result | Meaning |
| --- | --- |
| `201 Created` | Item stored successfully; `Location` identifies its GET address. |
| `200 OK` | Existing item returned. |
| `400 Bad Request` | Missing/invalid fields, malformed JSON/UUID, unsupported unit or incompatible JSON types. |
| `404 Not Found` | Valid identity with no stored item. |
| `409 Conflict` | Identity already exists; original attributes remain stored. |
| `500 Internal Server Error` | Unexpected persistence failure; database diagnostics are not returned. |

Error responses use `application/problem+json` with `title`, `status`, `detail` and `instance`. The default `type` is `about:blank` and can be omitted, as specified by [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html#section-3.1.1). Field-validation errors also include an `errors` list with `field` and `message`; rejected values are not echoed. HTTP error rendering uses Spring's [ProblemDetail support](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html).

Requests require `id`, a nonblank `name` and a `unit` from `PAIR`, `UNIT`, `LITRE`, `ROLL` or `KG`. Names preserve supported Unicode, whitespace and case, without an added length restriction. NUL and unpaired Unicode surrogates are rejected at the HTTP boundary because they cannot be preserved as PostgreSQL UTF-8 text. JSON numbers/booleans are not converted into text fields, and units cannot be submitted as numeric enum positions. Request/response records are separate from the domain and JPA classes; controller operations use the existing `ItemRepository` contract.

## Stock entries

```java
import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import io.github.josepauloferreira.vestigium.domain.stock.MovementQuantity;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntry;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntryId;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntryLine;
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

`occurredAt` is an explicitly supplied `Instant`: when the material was received, not when a record was typed. It must be exactly representable in microseconds: `getNano() % 1_000 == 0`. More precise values throw `IllegalArgumentException`; they are never rounded or truncated. The `Z` in the example denotes UTC. Converting operator-local date/time to an instant and displaying it in a timezone belong to future input/output boundaries. Accepted instants are preserved exactly; no current-time or future-date cutoff is imposed by the receipt constructor. Persisted receipts must not precede an established initial stock for any referenced item.

Each item may occur only once within an entry. Duplicate `ItemId` values throw `IllegalArgumentException`, even when wrapped by different objects or paired with different quantities. The caller supplies the intended total; entries never silently combine or round lines. The same item may appear in separate entries.

Missing required values or a null line throw `NullPointerException`; an empty line list throws `IllegalArgumentException`. `List.copyOf` preserves line order and protects entries against changes to the caller's collection. The exposed list is unmodifiable, and line components are immutable. Lombok generates read-only accessors; constructor validation and identity equality remain explicit.

Equal entry identifiers denote the same entity; identical occurrence/lines with different identifiers represent separate entries. A domain line references `ItemId` without performing database access. During persistence, foreign keys require every referenced item to exist in the catalog. Entry objects preserve receipt facts; StockBalanceCalculator derives totals separately. Stock-entry HTTP routes expose receipt creation/lookup; a correction workflow remains future work.

### Durable stock entries

`StockEntryRepository` exposes `insert(StockEntry)` and `findById(StockEntryId)`. Insertion is one transaction for the header and every line; failure rolls back all of that entry's writes. A repeated identity throws `DuplicateStockEntryIdException` without changing stored facts, even for an identical submission. An unknown identity returns `Optional.empty()`; an unknown item reference fails with a data-integrity error. Existing outer transactions are joined, so the caller still controls their final commit/rollback.

Flyway V2 creates `stock_entries` and `stock_entry_lines`. The header stores the supplied UUID and a finite `TIMESTAMP(6) WITH TIME ZONE`. Lines have an explicit zero-based position, an item foreign key and a positive finite quantity. Primary/unique constraints prevent repeated positions or items within an entry. The domain requires at least one line, and the application inserts the complete entry atomically. V1 stays unchanged; V2 upgrades an existing item catalog.

`StockEntryEntity` maps its owned line values using `@ElementCollection`, `@CollectionTable` and `@OrderColumn`. These lines have no separate entity identity or repository. A separate embeddable persistence record maps them while the domain `StockEntryLine` remains independent of JPA. Read operations reconstruct the domain inside a read-only transaction so lazy collection loading does not depend on an open web request.

Quantity storage uses unconstrained PostgreSQL `NUMERIC`, with a separate original `quantity_scale`. When needed to fit the fractional storage limit, the persistence mapping removes trailing zeroes from the stored magnitude. It reconstructs the original scale with `RoundingMode.UNNECESSARY`, preserving values such as `1.50` and `1E+3`. Before binding JDBC values, it rejects magnitudes outside PostgreSQL's physical limits: more than 131,072 integer digits or more than 16,383 necessary fractional digits. This is a storage boundary, not a new domain decimal policy. Unsupported quantities and dates outside the database range fail instead of becoming rounded values or infinity. See [PostgreSQL numeric types](https://www.postgresql.org/docs/18/datatype-numeric.html) and [date/time types](https://www.postgresql.org/docs/18/datatype-datetime.html).

## Stock-entry HTTP API

Register each referenced item through `POST /items` first. For this example, the item ending in `201` is the synthetic oil registered above. Register a second synthetic item:

```bash
curl --include --header 'Content-Type: application/json' \
  --data '{"id":"00000000-0000-0000-0000-000000000202","name":"Synthetic boots","unit":"PAIR"}' \
  http://127.0.0.1:8080/items
```

Then submit the receipt:

```bash
curl --include --header 'Content-Type: application/json' \
  --header 'Accept: application/json' \
  --data '{"id":"00000000-0000-0000-0000-000000000301","occurredAt":"2026-11-03T14:30:00.123456Z","lines":[{"itemId":"00000000-0000-0000-0000-000000000201","quantity":1.50},{"itemId":"00000000-0000-0000-0000-000000000202","quantity":4}]}' \
  http://127.0.0.1:8080/stock-entries
```

Expect `201 Created`, `Location: /stock-entries/00000000-0000-0000-0000-000000000301` and a JSON object containing `id`, `occurredAt` and ordered `lines` with `itemId` and `quantity`. Each line uses the referenced item's physical unit; there is no separate unit or conversion in the receipt.

```bash
curl --include --header 'Accept: application/json' \
  http://127.0.0.1:8080/stock-entries/00000000-0000-0000-0000-000000000301
```

Expect `200 OK` with the complete stored receipt, including after application restart. Decimal quantities bind directly to `BigDecimal` and retain their numeric value and scale; use JSON numbers, not quoted numeric strings. A consuming client must also avoid converting exact decimals through floating point if it needs that precision.

Supply `occurredAt` as an ISO 8601 date/time with `Z` or an explicit offset, such as `2026-11-03T11:30:00.123456-03:00`. The response expresses the same instant in UTC. Date-only and timezone-free input are rejected. The existing microsecond precision rule applies without rounding/truncation; no automatic future-date cutoff is applied. A receipt earlier than an established initial stock for any referenced item returns 409 without writing the receipt.

| Result | Meaning |
| --- | --- |
| `400 Bad Request` | Invalid JSON/types/UUIDs, missing fields, empty/null-containing lines, nonpositive quantities, repeated item IDs or unsupported occurrence precision. |
| `404 Not Found` | No entry with the requested valid identity. |
| `409 Conflict` | Entry ID already exists, even for identical contents; the original receipt remains unchanged. |
| `422 Unprocessable Content` | A referenced item is absent, or a specifically identified numeric/timestamp value cannot be stored exactly. |
| `500 Internal Server Error` | Unexpected persistence failure; it is not relabeled as a client input error. |

Errors follow the existing safe ProblemDetail format. Nested field errors identify paths such as `lines[0].quantity`. The persistence adapter translates the specific catalog foreign-key violation and known storage-range failures while preserving their causes; it does not classify arbitrary integrity errors by SQL message text. The entire receipt is one transaction: an unknown item in a later line leaves neither a header nor earlier lines stored. Different entry IDs may contain identical receipt facts; repeated items within one entry are rejected without summing.

## Movement quantity

```java
import io.github.josepauloferreira.vestigium.domain.stock.MovementQuantity;
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
import io.github.josepauloferreira.vestigium.domain.balance.StockBalanceCalculator;

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

Every supplied entry participates regardless of `occurredAt`. This is a total over explicit input, without an implicit current-time cutoff or historical-period query. The overload accepting InitialStock adds its quantity, checks item identity and rejects matching receipts before its occurrence. Exits, returns, adjustments and negative-stock policy remain future work.

The first matching quantity is used directly before adding subsequent quantities. This avoids an unnecessary zero addition that cannot align every valid extreme `BigDecimal` scale; a regression test covers the minimum supported scale.

## Item balance and receipt history

After registering the synthetic items/receipt above, record another receipt of 0.75 L for the item ending in `201`:

```bash
curl --include --header 'Content-Type: application/json' \
  --data '{"id":"00000000-0000-0000-0000-000000000302","occurredAt":"2026-11-03T15:30:00Z","lines":[{"itemId":"00000000-0000-0000-0000-000000000201","quantity":0.75}]}' \
  http://127.0.0.1:8080/stock-entries
```

Query its entry-derived balance:

```bash
curl --include http://127.0.0.1:8080/items/00000000-0000-0000-0000-000000000201/balance
```

Expect `200 OK` and:

```json
{
  "itemId": "00000000-0000-0000-0000-000000000201",
  "unit": "LITRE",
  "quantity": 2.25
}
```

Explain that total through its recorded receipts:

```bash
curl --include http://127.0.0.1:8080/items/00000000-0000-0000-0000-000000000201/stock-entries
```

Expect `200 OK` with `itemId`, `unit` and an `entries` array. Each entry contains `entryId`, UTC `occurredAt` and this item's exact `quantity`. The example yields 1.50 and 0.75 in occurrence order. Equal timestamps are ordered by the database UUID order. The existing `GET /stock-entries/{id}` returns the complete multi-item receipt.

A known item without initial stock or receipts has numeric zero and an empty receipt history; an unknown catalog identity returns 404, and an invalid UUID returns 400. Unexpected persistence failures retain the safe 500 ProblemDetail response.

All recorded occurrence times participate, including future-dated entries; the queries apply no automatic current-time cutoff. History returns all receipts for the selected item, without pagination/date filters or truncation. The quantity is the exact total of any initial stock plus recorded receipts; exits, returns and adjustments remain future work. Receipt history excludes the initial-stock fact, which has its own lookup. The result does not establish a physical count. BigDecimal arithmetic has no fixed display scale, and individual history quantities retain their original scale.

The application coordinates catalog lookup, a JPA query for matching complete receipts and StockBalanceCalculator. It fetches their line collections together, preserving complete aggregates and avoiding a collection query for every receipt. Flyway V3 adds `stock_entry_lines_item_id_idx` for the new item-filtered access path; V1/V2 remain unchanged. This index has storage/write cost; no measured speedup is asserted. Query plans depend on data and PostgreSQL's planner.

## Initial stock

An item's opening is a known starting quantity, recorded at most once. `InitialStockQuantity` is a nonnegative decimal Value Object; its record equality/hash use numeric meaning while preserving scale. `MovementQuantity` stays strictly positive. A missing opening and a recorded zero are different facts.

After the receipt examples above, establish the synthetic item's initial 50.00 L before those receipts:

```bash
curl --include --header 'Content-Type: application/json' \
  --data '{"quantity":50.00,"occurredAt":"2026-11-03T08:00:00Z"}' \
  http://127.0.0.1:8080/items/00000000-0000-0000-0000-000000000201/initial-stock
```

Expect 201 and a Location for the same `/initial-stock` resource. GET that address returns itemId, unit, quantity and UTC occurredAt. Its quantity retains 50.00; the balance now includes this opening plus the earlier receipt examples (52.25 L after receipts of 1.50 and 0.75). A repeated POST returns 409 without overwriting the original, even when identical. A known item without an opening returns 404 with `Initial stock has not been recorded for this item`; an absent catalog item returns 404 with `Item was not found`. An explicit 0.0000 is accepted and remains retrievable as a recorded zero.

The opening occurs immediately before receipts at the same timestamp. It may be entered after receipts have already been saved, provided its occurrence precedes or equals their earliest occurrence. Opening after an existing receipt or inserting a receipt before an existing opening returns 409. Negative quantities, quoted numbers, invalid UUIDs and invalid offset/microsecond dates return 400; identified unsupported exact storage values return 422; unexpected persistence failures return a generic 500. No automatic future-date cutoff is added.

V4 creates a separate initial_stock table with a per-item primary/foreign key and database checks for nonnegative finite quantity and finite timestamp. Original scale is stored separately. Existing migrations and receipts remain unchanged. Both supported write paths lock referenced catalog rows in database UUID order inside their transaction, then validate chronology before saving. Unknown receipt references are rejected by that locking lookup; PostgreSQL foreign keys remain independently enforced. Direct SQL bypasses application chronology coordination; future import/write paths must use the same protocol. A conflicting multi-item receipt leaves no partial data.

Balance and receipt-history HTTP queries use a read-only repeatable-read transaction so catalog, receipts and opening come from one snapshot. Real HTTP tests hold a table lock, commit an opening/receipt concurrently and verify a complete old result followed by the complete new result. Write-race tests observe actual PostgreSQL lock waits, rather than assuming a timed delay proves contention.

## OpenAPI and Swagger UI

With the application running on its default port, open:

- [Swagger UI](http://127.0.0.1:8080/swagger-ui/index.html) to browse operations and issue requests.
- [OpenAPI JSON](http://127.0.0.1:8080/v3/api-docs) for the machine-readable description.
- [OpenAPI YAML](http://127.0.0.1:8080/v3/api-docs.yaml) for the YAML representation.

Adjust the port if SERVER_PORT is configured. The pinned `springdoc-openapi-starter-webmvc-ui` 3.2.0 generates the description from controller mappings, transport models, validation and focused OpenAPI metadata. Its Spring Boot 4.1 compatibility is documented in the [official matrix](https://springdoc.org/faq.html); endpoint defaults are in the [springdoc documentation](https://springdoc.org/).

The description includes item/receipt creation and lookup, item balances and item receipt history, with required fields, canonical UUIDs, exact numeric semantics, synthetic examples, Location headers and the actual ProblemDetail statuses. Swagger UI uses this application's description. Requests executed in Swagger UI perform real operations against the configured database; use synthetic review data.

Register an item first, then reference that ID when recording a receipt. Use JSON numeric quantities and explicit-offset/Z occurrence times with microsecond precision. OpenAPI describes quantities as numbers without promising binary double precision. Browser/JavaScript clients may normalize displayed decimal values; consumers needing exact decimal semantics should use suitable numeric types.

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

Spring Boot Actuator supplies this endpoint; there is no custom health controller. Among management endpoints, only `health` is exposed over HTTP, without component details. Separate liveness/readiness probe groups remain disabled. The configured checks now include the database connection. Normal application startup requires the configured database and successful Flyway initialization; `UP` does not establish a complete inventory workflow.

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

`ItemHttpTest` drives the item API through a real HTTP server and PostgreSQL. It verifies creation/Location, exact responses, all units, Unicode/long names, invalid requests without writes, safe ProblemDetail errors and unchanged original data after conflicts. It also closes/reopens HTTP application contexts to retrieve committed data. A test-only table rename provokes a real persistence failure and verifies a generic 500 response; the table is restored in the disposable database before the test finishes.

`StockEntryHttpTest` verifies the request-to-database-to-response path for ordered receipts, exact decimals/times, nested validation, unknown references, duplicate identities, safe server failures and retrieval across application restarts. Repository tests retain the late-line failure probe that verifies transaction rollback.

`ItemStockHttpTest` verifies item isolation, exact totals, zero versus missing items, ordered/scaled receipt summaries and safe failures. Repository tests verify complete aggregate loading in one SQL statement and V2-to-V3 preservation. `OpenApiHttpTest` checks the generated paths, statuses, schemas, decimal types, YAML and Swagger UI resources.

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
