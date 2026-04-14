# ATPEZMS Phase 1 (Ticketing) - Implementation Notes

This document specifies **how Phase 1 Ticketing will be implemented** in code, consistent with:

- `DESIGN.md` (global blueprint)
- `IMPLEMENTATION.md` (global conventions)
- `PHASE_01_TICKETING_DESIGN.md` (slice design)

It intentionally focuses on implementation-level decisions (dependencies, packages, transactions, migrations, validation, tests) rather than repeating the domain rationale.

---

## 1. Dependencies Introduced In Phase 1 (JIT / YAGNI)

Phase 1 is the first slice that requires HTTP endpoints and database persistence. To keep changes educational and reviewable, dependencies are introduced in **fine-grained commits**.

Phase 1.1 first introduces the web layer and request validation:

- `spring-boot-starter-webmvc` (Spring MVC REST API)
- `spring-boot-starter-validation` (Jakarta Bean Validation on request DTOs)

Then, when we begin implementing persistence and migrations, we add:

- `spring-boot-starter-data-jpa` (repositories + transactions)
- `spring-boot-starter-flyway` (schema migrations)
- `com.h2database:h2` (embedded database)

Rationale:

- These are the minimum to implement Ticketing endpoints backed by a relational schema.
- We still avoid adding unrelated integrations (security, metrics, PDF, etc.) until their slices.

---

## 2. Spring Profiles And Database Modes

We will implement the global profile rules from `IMPLEMENTATION.md`:

- `dev`: H2 file mode (persistent) with H2 console enabled for local inspection.
- `test`: H2 in-memory mode, used by automated tests.

Phase 1 adds configuration files:

- `application-dev.properties`
- `application-test.properties`

Key settings:

- Flyway enabled in both profiles.
- `spring.jpa.hibernate.ddl-auto=validate` so schema is controlled by Flyway.

---

## 3. Package Layout For Phase 1

We follow package-by-context and layer subpackages.

New packages (created only when populated):

- `com.atpezms.atpezms.ticketing.controller`
- `com.atpezms.atpezms.ticketing.service`
- `com.atpezms.atpezms.ticketing.repository`
- `com.atpezms.atpezms.ticketing.entity`
- `com.atpezms.atpezms.ticketing.dto`

Because Phase 1 seeds Park reference tables and needs read access, we introduce a minimal Park query surface:

- `com.atpezms.atpezms.park.service` (read-only queries)
- `com.atpezms.atpezms.park.repository`
- `com.atpezms.atpezms.park.entity`

Park controllers (CRUD) are deferred to Phase 2.

---

## 4. Entities And JPA Mapping Rules

### 4.1 Base Entity And Auditing

Phase 1 is the first slice that persists entities, so it introduces the `common.entity.BaseEntity` mapped superclass and enables JPA auditing as described in `IMPLEMENTATION.md`.

Why now:

- We need `createdAt/updatedAt` on Ticketing records for traceability.
- Centralizing these fields avoids repeating them across every entity.

### 4.2 Explicit Table/Column Names

All entities use explicit `@Table` / `@Column` names to match the schema exactly and keep mapping visible.

### 4.3 Constraints And Indexes

We implement database constraints that enforce invariants early:

- UNIQUE on `wristbands.rfid_tag`
- UNIQUE on `pass_types.code`
- UNIQUE on the `pass_type_prices` price-matrix key
- UNIQUE on `park_day_capacity.visit_date`

Performance index for hot path:

- Index on `wristbands.rfid_tag`
- Index on `visits.wristband_id` and `visits.status` (active visit lookup)

---

## 5. PII Encryption Mechanism

Phase 1 must satisfy SE-1 for visitor PII.

Implementation choice:

- JPA `AttributeConverter<String, String>` that encrypts/decrypts selected String fields.

Encryption details (kept simple and explainable):

- Algorithm: AES-GCM (authenticated encryption).
- Stored form: Base64 encoding of `iv || ciphertext || tag`.
- Key: provided via an environment variable (not committed).

Rules:

- Only PII fields (name/email/phone) use the converter.
- Never log plaintext PII.
- Never log ciphertext either, because it is still sensitive.

Testing:

- Unit tests verifying that converter round-trips values and produces different ciphertext for the same plaintext (random IV).

---

## 6. Services And Transaction Boundaries

Primary service operations:

- `VisitorService.createVisitor(...)`
- `PassTypeService.listActivePassTypes()`
- `VisitService.issueTicketAndStartVisit(...)` (the Phase 1 core use case)
- `RfidResolutionService.resolveActiveVisitByRfidTag(...)`

Transaction rules:

- `issueTicketAndStartVisit` is `@Transactional`.
- Capacity increment, wristband activation, ticket creation, and visit creation happen inside the same transaction.
- If any invariant fails (capacity exceeded, wristband already active, visitor not found), we throw a runtime exception from the global exception hierarchy so Spring rolls back the transaction.

Capacity enforcement implementation:

- Implement the guarded increment as a repository `@Modifying` update query and check the affected row count.
- If the day row does not exist, insert it (with max capacity copied from active park configuration) and retry once.

---

## 7. Controllers And DTO Validation

Controllers are thin and delegate to services.

DTO rules:

- Request DTOs are Java records in `ticketing.dto`.
- Validation annotations enforce shape at the boundary.

Example validations we will apply:

- `firstName/lastName`: not blank, length bounds
- `heightCm`: min/max range
- `rfidTag`: not blank, length bounds
- IDs: not null and positive

---

## 8. Flyway Migration Plan

Phase 1 introduces the first schema, so Flyway starts at `V001`.

Planned migrations:

1. `V001__create_phase_1_ticketing_and_park_reference.sql`

Contents:

- Create Park reference tables (`zones`, `park_configurations`, `seasonal_periods`)
- Create Ticketing tables (`visitors`, `wristbands`, `pass_types`, `pass_type_prices`, `tickets`, `visits`, `access_entitlements`, `park_day_capacity`)
- Create indexes and uniqueness constraints
- Seed minimal reference/config data:
  - starter zones
  - one active park configuration with a placeholder `max_daily_capacity`
  - a starter set of seasonal periods
  - pass types and a starter price matrix

Important:

- Seed values are placeholders for development and will be updated once real values are agreed.

---

## 9. Tests Produced In Phase 1

Per `IMPLEMENTATION.md` we write:

- Integration tests (`@SpringBootTest`, `@ActiveProfiles("test")`) for `issueTicketAndStartVisit`:
  - creates visitor + issues ticket + starts visit
  - denies when capacity exceeded
  - denies when wristband already active
- Controller tests (`@WebMvcTest`, `@ActiveProfiles("test")`) to verify validation and error mapping:
  - 400 on invalid DTOs
  - correct status codes for known business exceptions

---

## 10. Open Items To Confirm Before Coding

Design decisions confirmed:

1. Seed values: acceptable as placeholders for development.
2. PII encryption policy: encrypt name/email/phone; keep non-PII operational attributes unencrypted.
3. Multi-day capacity rule (when enabled): reserve daily capacity for every day in the ticket validity window.

Phase 1.1 implementation note:

- We will start with single-day issuance and defer multi-day issuance to Phase 1.2 (per `PHASE_01_TICKETING_DESIGN.md`).
