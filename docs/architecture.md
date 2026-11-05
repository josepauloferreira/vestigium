# Layers organized by module

The existing top-level layers stay in place. Modules group related classes inside each layer, so expanding domain/item shows the catalog's business objects and application/item shows its application boundary. Infrastructure retains persistence and web, each grouped by module.

This is package organization in one Maven project and one executable Spring Boot application. These directories are not separate deployments or compiler-enforced Maven modules.

## Package map

Under src/main/java/io/github/josepauloferreira/vestigium:

```text
VestigiumApplication.java
domain/
  item/          Item, ItemId and PhysicalUnit
  stockentry/    StockEntry, StockEntryId and StockEntryLine
  initialstock/  InitialStock and InitialStockQuantity
  balance/       StockBalanceCalculator
  stock/         MovementQuantity shared by physical movements
application/
  item/          Catalog repository interface and failures
  stockentry/    Receipt repository interface and failures
  initialstock/  Opening use cases, repository interface and failures
  balance/       ItemStockQueries and query results
  stock/         Cross-module chronology conflict
infrastructure/
  persistence/
    item/          Item JPA mapping and repository adapter
    stockentry/    Receipt/line mappings and repository adapter
    initialstock/  Opening mapping and repository adapter
    stock/         Initial-stock/receipt write coordination
  web/
    item/          Catalog controller and request/response records
    stockentry/    Receipt controller and request/response records
    initialstock/  Opening controller, records and bean wiring
    balance/       Balance/history routes, responses and bean wiring
    shared/        Error handling, JSON, canonical UUIDs and OpenAPI
```

## Layer responsibilities

| Layer | Responsibility | Read it to understand... |
| --- | --- | --- |
| domain | Business objects, validation, identity and exact calculation | Which facts are valid and how balances are derived |
| application | Use cases, queries, repository interfaces and explicit failures | Which facts an operation needs |
| infrastructure.persistence | Hibernate mapping, repository adapters, queries and transaction coordination | How facts are stored/retrieved consistently |
| infrastructure.web | HTTP contracts, controllers and feature wiring | Request JSON, response JSON and statuses |

Domain and application code remain independent of Spring, Hibernate and Swagger. Repository interfaces belong to application; implementations and mutable JPA mappings belong to infrastructure.persistence. The immutable domain object and its persistence mapping have different responsibilities.

The item module provides identity/unit context used by stock. Stock entries and initial stock describe different facts. Balance combines those facts without storing a separately editable total. Common stock code is limited to the quantity and chronology concerns actually shared across modules; shared web code contains technical HTTP conventions.

## Follow a receipt request

For POST /stock-entries, read these files in order:

1. infrastructure/web/stockentry/CreateStockEntryRequest.java defines accepted JSON and nested field validation.
2. StockEntryController.java in the same directory maps it into the objects in domain/stockentry.
3. application/stockentry/StockEntryRepository.java names the insert operation at the application boundary.
4. infrastructure/persistence/stockentry/JpaStockEntryRepository.java implements insertion inside a transaction.
5. infrastructure/persistence/stock/StockTrackingGuard.java coordinates catalog row locks and initial-stock chronology before writing.
6. StockEntryEntity.java and StockEntryLineValue.java in the stockentry persistence package map the complete ordered facts to PostgreSQL.

For a balance, start with infrastructure/web/balance/ItemStockController.java, then application/balance/ItemStockQueries.java and domain/balance/StockBalanceCalculator.java. The HTTP read transaction retrieves catalog context, receipts and opening from one consistent snapshot.

## Shared HTTP conventions and tests

CanonicalUuid centralizes the format used by controllers, receipt field validation and OpenAPI. Domain IDs still wrap UUID values and have no HTTP dependency. OpenApiConfiguration separates error schemas, route parameters, responses and request schemas into named methods so each concern can be read separately.

Tests mirror the layer/module packages under src/test/java. Domain tests cover business rules; web tests exercise actual HTTP/PostgreSQL; persistence tests cover mapping, migrations, atomic writes and observed concurrency. PostgresTestConfiguration remains a common test fixture at the root.

VestigiumApplication stays above all these packages so the existing application discovers the controllers, configuration and JPA mappings. Migrations, endpoint paths, JSON fields and operation IDs are unchanged by this organization.

Keep related module names consistent across layers. Add a new module when an agreed business responsibility needs one; avoid additional subdivisions for a handful of closely related files.
