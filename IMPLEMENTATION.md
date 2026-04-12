# ATPEZMS -- Global Implementation Standards

This document defines implementation-level conventions and patterns that apply across all vertical slices. It is the bridge between `DESIGN.md` (architectural what/why) and per-slice implementation (specific code).

## Living Document

This document **evolves continuously** as slices are built. When a slice reveals that a convention is impractical, or introduces a pattern that should be standardized, this document is updated to reflect reality. The rule from `DESIGN_RULES.md` applies here too: the blueprint must reflect reality, and reality must reflect the blueprint.

Changes to this document should be deliberate: if a slice needs to deviate from a convention here, the deviation is discussed first, and if accepted, this document is updated so all future slices follow the new convention. Ad-hoc inconsistencies between slices are not acceptable.

---

## 1. Project Structure

### 1.1 Package Layout

Base package: `com.atpezms.atpezms`

Each bounded context is a direct sub-package. Within each context, code is organized by technical layer:

```
com.atpezms.atpezms.<context>.controller/
com.atpezms.atpezms.<context>.service/
com.atpezms.atpezms.<context>.repository/
com.atpezms.atpezms.<context>.entity/
com.atpezms.atpezms.<context>.dto/
```

The `common` package holds shared infrastructure that is not specific to any bounded context:

```
com.atpezms.atpezms.common.exception/
com.atpezms.atpezms.common.dto/
com.atpezms.atpezms.common.config/
com.atpezms.atpezms.common.entity/
```

Sub-packages are created only when the context has classes of that type. An empty `dto/` package is not created preemptively.

### 1.2 Gradle Dependencies

Dependencies are added incrementally as slices need them, not all upfront. The Global Infrastructure slice (Phase 0) adds the baseline: `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, H2, and Flyway. Later slices add dependencies only if they introduce a genuinely new capability (e.g., a PDF library in the Analytics slice).

---

## 2. Naming Conventions

### 2.1 Java

| Element | Convention | Example |
|---------|-----------|---------|
| Packages | lowercase, singular context name | `com.atpezms.atpezms.ticketing` |
| Entities | PascalCase, singular noun | `Visitor`, `Ride`, `Transaction` |
| Controllers | `<Domain>Controller` | `VisitorController`, `RideController` |
| Services | `<Domain>Service` (concrete class, no interface) | `VisitorService`, `BillingService` |
| Repositories | `<Entity>Repository` | `VisitorRepository`, `RideRepository` |
| Request DTOs | `<Action><Domain>Request` | `CreateVisitorRequest`, `UpdateRideRequest` |
| Response DTOs | `<Domain>Response` | `VisitorResponse`, `RideResponse` |
| Exceptions | `<Description>Exception` | `CapacityExceededException`, `WristbandNotFoundException` |
| Configuration | `<Concern>Config` | `SecurityConfig`, `JpaAuditConfig` |

**Services are concrete classes, not interface + implementation pairs.** The interface-impl pattern (e.g., `VisitorService` interface + `VisitorServiceImpl`) adds indirection without value in a monolith where there is exactly one implementation. Spring's `@MockBean` handles test doubles. If a genuine need for multiple implementations arises (e.g., the discount extension point for Loyalty), an interface is introduced at that point for that specific case.

### 2.2 Database

| Element | Convention | Example |
|---------|-----------|---------|
| Tables | snake_case, plural | `visitors`, `ride_sessions` |
| Columns | snake_case | `wristband_id`, `created_at` |
| Foreign keys | `<referenced_table_singular>_id` | `visitor_id`, `ride_id` |
| Join tables | `<table1>_<table2>` alphabetical | `passes_zones` |
| Indexes | `idx_<table>_<column(s)>` | `idx_visitors_rfid_tag` |
| Unique constraints | `uk_<table>_<column(s)>` | `uk_wristbands_rfid_tag` |

JPA's `@Table` and `@Column` annotations are used to explicitly set table and column names rather than relying on Hibernate's implicit naming strategy. This makes the mapping visible in the entity class and prevents surprises from naming strategy changes.

### 2.3 REST API

| Element | Convention | Example |
|---------|-----------|---------|
| Base path | `/api` prefix on all endpoints | `/api/visitors` |
| Resources | lowercase, plural, kebab-case | `/api/visitors`, `/api/ride-sessions` |
| Identifiers | Path variable | `/api/visitors/{id}` |
| Non-CRUD actions | Verb sub-resource | `/api/wristbands/{id}/scan`, `/api/emergency/override` |
| Filtering | Query parameters | `/api/visitors?passType=FAMILY&status=ACTIVE` |
| Pagination | Query parameters | `/api/rides?page=0&size=20&sort=name,asc` |

**HTTP methods and status codes:**

| Operation | Method | Success Status |
|-----------|--------|---------------|
| Create | POST | 201 Created |
| Read one | GET | 200 OK |
| Read collection | GET | 200 OK |
| Full update | PUT | 200 OK |
| Partial update | PATCH | 200 OK |
| Delete | DELETE | 204 No Content |

---

## 3. Base Entity

All domain entities extend a common mapped superclass that provides:

| Field | Type | Behavior |
|-------|------|----------|
| `id` | `Long` | Auto-generated primary key. |
| `createdAt` | `Instant` | Set automatically on first persist. Never modified after. |
| `updatedAt` | `Instant` | Set automatically on every persist and update. |

These fields are populated by JPA auditing (`@EntityListeners(AuditingEntityListener.class)` with `@CreatedDate` / `@LastModifiedDate`). This requires a `JpaAuditConfig` class annotated with `@EnableJpaAuditing` in the `common.config` package.

Entities that require accountability (per `DESIGN.md` Section 3.8) additionally carry `createdBy` and `updatedBy` (type `String`), populated via `@CreatedBy` / `@LastModifiedBy`. These require an `AuditorAware` bean that returns the current authenticated user's identifier. Before the security slice, this returns a placeholder value (e.g., `"system"`). After security, it reads from the JWT.

---

## 4. Exception Handling

### 4.1 Hierarchy

A single abstract base exception. Concrete exceptions extend category classes that map to HTTP status codes. Every exception carries an `errorCode` string (machine-readable, e.g., `"CAPACITY_EXCEEDED"`) and a `message` string (human-readable).

```
RuntimeException
  └── BaseException (abstract)
        ├── ResourceNotFoundException                    → 404
        ├── DuplicateResourceException                   → 409
        ├── StateConflictException                       → 409
        └── BusinessRuleViolationException               → 422
              ├── CapacityExceededException
              ├── InsufficientStockException
              ├── EligibilityDeniedException
              ├── OutstandingBalanceException
              └── SlotConflictException
```

New exception types are added by slices as needed. They must extend one of the four category classes, not `BaseException` directly, so that the global exception handler's HTTP status mapping remains automatic.

### 4.2 Global Exception Handler

A single `@RestControllerAdvice` class in `common.exception` handles all exceptions. Controllers never catch exceptions themselves.

**Error response body:**

```json
{
  "status": 422,
  "code": "CAPACITY_EXCEEDED",
  "message": "Park has reached maximum daily capacity",
  "timestamp": "2026-04-12T10:30:00Z",
  "fieldErrors": []
}
```

For validation failures (`MethodArgumentNotValidException`), `fieldErrors` is populated:

```json
{
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "timestamp": "2026-04-12T10:30:00Z",
  "fieldErrors": [
    { "field": "name", "message": "must not be blank" },
    { "field": "age", "message": "must be greater than 0" }
  ]
}
```

The handler also catches Spring-provided exceptions:

| Spring Exception | Response |
|-----------------|----------|
| `MethodArgumentNotValidException` | 400 + field errors |
| `HttpMessageNotReadableException` | 400 (malformed JSON) |
| `HttpRequestMethodNotSupportedException` | 405 |
| `NoHandlerFoundException` | 404 (Requires spring.mvc.throw-exception-if-no-handler-found=true) |
| Unhandled `Exception` | 500 (generic message, no internal details leaked) |

### 4.3 Error Response DTO

An `ErrorResponse` record in `common.dto` models the error body above. A nested `FieldError` record holds field-level validation details. These are used only by the global exception handler -- controllers and services never construct them directly.

---

## 5. DTO Conventions

- Request and response DTOs are Java records (immutable, concise).
- Request DTOs carry Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Size`, `@Min`, etc.).
- Response DTOs do not carry validation annotations.
- Controller method parameters annotated with `@Valid` trigger automatic validation before the method body executes.
- Mapping between entities and DTOs is done in the service layer with explicit constructor/factory-method calls. No mapping framework (MapStruct, ModelMapper) is used -- manual mapping is more transparent and easier to explain in a course context.
- DTOs live in their context's `dto` package, not in `common`. If a response shape is shared across contexts, evaluate whether it genuinely belongs in `common.dto` or whether each context should define its own version.

---

## 6. Repository Conventions

- Repositories are interfaces extending `JpaRepository<Entity, Long>`.
- Custom query methods use Spring Data's method-name derivation where possible (e.g., `findByRfidTag`).
- Complex queries use `@Query` with JPQL. Native SQL is avoided unless JPQL is genuinely insufficient.
- Repositories belong to their context's `repository` package.
- Analytics repositories (read-only, cross-context) belong to the `analytics.repository` package and must only contain read operations.

---

## 7. Service Layer Conventions

- Services are `@Service`-annotated concrete classes.
- Transaction boundaries are managed via `@Transactional` on service methods, not on controllers or repositories.
- Read-only operations use `@Transactional(readOnly = true)` for performance hints.
- Cross-context service calls participate in the caller's transaction **only** when they are part of a single atomic business operation that must satisfy CO-2 (e.g., "record Billing transaction + decrement inventory").
- Best-effort side effects (Telemetry scan logging, external gateways like TurnstileGateway, metrics/logging) **must not** cause the primary operation to fail. Wrap these calls in try/catch and log failures; if they require DB writes, isolate them in `@Transactional(propagation = Propagation.REQUIRES_NEW)` or execute them asynchronously so failures do not roll back the primary transaction.
- Services accept and return DTOs or simple values to/from controllers. They work with entities internally.

---

## 8. Controller Layer Conventions

- Controllers are `@RestController`-annotated classes with a `@RequestMapping("/api/<resource>")` base path.
- Each endpoint method is documented with a comment specifying the required security role (e.g., `// Requires: ROLE_TICKET_STAFF`). After the security slice, these become `@PreAuthorize` annotations.
- Controllers delegate all work to services. The only logic in a controller is: accept the request, call the service, return the response.
- Path variables use `@PathVariable`, request bodies use `@RequestBody @Valid`, query parameters use `@RequestParam` or a parameter object.
- Controllers return `ResponseEntity<T>` for explicit status code control (e.g., `ResponseEntity.status(201).body(response)`).

---

## 9. Database and Migration

### 9.1 H2 Configuration

Two Spring profiles control database behavior:

- **`default` (development):** H2 in file mode. Data persists across application restarts. The H2 console is enabled for direct database inspection during development ONLY. It MUST NOT be enabled in any shared/staging/production-like environment due to security risks.
- **`test`:** H2 in-memory mode. Each test run starts with a clean database. Fast, isolated, no cleanup needed.

### 9.2 Flyway Migrations

Schema changes are managed by Flyway, not Hibernate DDL auto-generation. `spring.jpa.hibernate.ddl-auto` is set to `validate` -- Hibernate checks that entities match the schema but never modifies it.

Migration files live in `src/main/resources/db/migration/` and follow Flyway's naming convention:

```
V<version>__<description>.sql
```

Version numbers use a simple incrementing integer scheme: `V001`, `V002`, etc. Each vertical slice produces one or more migration files for its schema changes. The description should identify the slice and purpose:

```
V001__create_base_infrastructure.sql
V002__create_park_zones_and_config.sql
V003__create_identity_users_and_roles.sql
```

### 9.3 Concurrency & Data Integrity

Rules to enforce invariants under load (CO-2, PR-2):
- Enforce invariants with database constraints first (UNIQUE, NOT NULL, CHECK) and treat service-layer checks as secondary.
- For high-contention counters/resources (inventory quantity, ride session capacity, reservation capacity), use either:
  - atomic update queries with guarded WHERE clauses (e.g., `UPDATE ... WHERE qty >= n`) and verify affected row count, or
  - pessimistic locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on the row(s) that define capacity, inside a single `@Transactional` boundary.
- Consider `@Version` optimistic locking for mutable aggregate roots where "last write wins" is incorrect.

---

## 10. Testing Strategy

### 10.1 Test Levels

Each slice produces tests at two levels:

- **Integration tests (`@SpringBootTest`):** Boot the full application context, exercise the real service and repository layers against the in-memory H2 database. These verify that the slice works end-to-end, including transaction behavior and cross-context service calls.
- **Controller tests (`@WebMvcTest`):** Test the controller layer in isolation. The service layer is mocked. These verify request/response mapping, validation, and error handling without touching the database.

Unit tests for pure business logic methods are written where valuable but are not mandated for every class. Service methods that are thin delegation to a repository don't need a dedicated unit test -- the integration test covers them.

### 10.2 Test Data

Tests construct their own data. No shared test fixtures or static seed data across tests. Each test method sets up what it needs and cleans up via transaction rollback (the default behavior with `@SpringBootTest` and `@Transactional` on the test class).

### 10.3 Test Naming

Test methods use descriptive names that state the scenario and expected outcome:

```
shouldReturnVisitorWhenRfidTagExists()
shouldReturn404WhenVisitorNotFound()
shouldDenyRideAccessWhenHeightBelowMinimum()
```

---

## 11. External Interface Mocks

Each external interface from `DESIGN.md` Section 7 is implemented as:

1. A Java interface in `common` (or the owning context if context-specific) defining the contract.
2. A mock implementation annotated with `@Service` and `@Profile({"dev", "test"})`.

The mock implementations log their calls (via SLF4J logger) and return canned success responses. Mock implementations MUST be active only under `dev` or `test` profiles. Real implementations MUST be the default for any non-dev profile to avoid accidental mock usage.

---

## 12. Idempotency

Device-originated write requests MUST include an idempotency key (string). The server guarantees at-most-once processing per (client/device identity, idempotencyKey).

Implementation standard:
- Persist keys in an `idempotency_keys` table with a UNIQUE constraint on `(client_id, idempotency_key)`.
- Store: `client_id`, `idempotency_key`, `request_hash`, `status` (IN_PROGRESS|COMPLETED), `response_status`, `response_body`, `created_at`.
- On first-seen key: insert row as IN_PROGRESS, execute the operation, then store the response and mark COMPLETED.
- On duplicate key:
  - If COMPLETED: return the stored `response_status` + `response_body` (do not re-execute).
  - If IN_PROGRESS: return 409 with code `IDEMPOTENCY_IN_PROGRESS` (caller retries with same key).
- Keys expire via cleanup (e.g., delete rows older than 7 days) to bound storage.

---

## 13. Conventions Not Yet Established

The following will be defined as the relevant slices are built. Placeholder sections are listed here so they are not forgotten:

- **Pagination defaults:** Default page size, maximum page size. Established when the first paginated endpoint is built.
- **Date/time serialization:** ISO 8601 format, timezone handling. Established in the Global Infrastructure slice.
- **Logging conventions:** Log levels, what to log at each level, structured logging format. Established as patterns emerge.
- **PII encryption mechanism:** JPA attribute converter or equivalent. Established in the Ticketing slice when Visitor PII fields are implemented.
- **Audit trail implementation:** High-impact administrative actions MUST be logged to an append-only `audit_log` table: `id`, `occurred_at`, `actor`, `action`, `resource_type`, `resource_id`, `details_json`, `ip`, `user_agent`. Audit records are INSERT-only. High-impact endpoints MUST write an audit record in the same request handling flow.
- **Discount extension point:** Interface design for the Loyalty discount hook in Food/Merchandise. Established in the Food slice (Phase 7) as a no-op, replaced in the Loyalty slice (Phase 11).
