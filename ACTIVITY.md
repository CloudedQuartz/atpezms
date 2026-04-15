# Project Activity Log

## 2026-04-12 - Established Global Blueprint And Standards
Created the Level 1 global architecture blueprint (`DESIGN.md`) and global implementation standards (`IMPLEMENTATION.md`) under the Iterative Waterfall rules. Defined bounded contexts, cross-context interaction rules, transaction and consistency rules (including commit-based idempotency), and global error-handling conventions.
This set the baseline that all future vertical slices must align to.

## 2026-04-12 - Shifted Roadmap To JIT (YAGNI) Dependencies
Removed a dedicated upfront "infrastructure phase" from the roadmap and adopted a Just-In-Time (YAGNI) approach where dependencies and shared infrastructure are introduced only when a vertical slice actually needs them. Set Ticketing as the foundational Phase 1 slice.
Documented that other contexts (Park management APIs, Identity/Security, Billing, etc.) will be implemented after Ticketing.

## 2026-04-12 - Drafted Phase 1 Ticketing Slice Docs
Created dedicated Phase 1 slice documents for Ticketing design and implementation (`PHASE_01_TICKETING_DESIGN.md`, `PHASE_01_TICKETING_IMPLEMENTATION.md`). Adopted a pragmatic approach where minimal Park reference/config tables are seeded via Flyway during Phase 1 to unblock Ticketing pricing and capacity enforcement, while deferring Park CRUD APIs to Phase 2.

## 2026-04-13 - Added Common Exception Hierarchy Baseline
Introduced the shared exception taxonomy (`BaseException`, `ResourceNotFoundException`, `DuplicateResourceException`, `StateConflictException`, `BusinessRuleViolationException`) to normalize error semantics before controller work begins. Also standardized 422 handling with `HttpStatus.UNPROCESSABLE_ENTITY` to keep status intent explicit and avoid magic-number lookups.

## 2026-04-13 - Implemented Global Error Handling (Common)
Implemented the shared `ErrorResponse` DTO and a centralized `@RestControllerAdvice` (`GlobalExceptionHandler`) that maps domain exceptions and validation failures into consistent JSON error responses. Added explicit server-side logging for unexpected exceptions and completed handler coverage with tests for state conflicts and generic 500 behavior without leaking internal exception details.

## 2026-04-13 - Required Same-Session Documentation Sync Workflow
Added an explicit rule that non-trivial changes must re-read relevant project docs before coding and update the affected documentation in the same work session. This formalizes anti-drift behavior so design intent and implementation stay aligned commit by commit.

## 2026-04-13 - Added Validation and Persistence Starters Baseline
Added Spring starters for Bean Validation, JPA, and Flyway plus the H2 runtime dependency to establish the baseline dependency set for request validation and database-backed slices. This enables DTO constraint enforcement at the API boundary and prepares the project for migration-owned schema management in upcoming commits.

## 2026-04-13 - Added Persistence Profiles and Flyway Baseline Schema
Introduced profile-specific datasource configuration for local development and tests, enforced Flyway migration ownership with Hibernate `ddl-auto=validate`, and added the initial V001 schema/seed migration that bootstraps Park reference data plus Ticketing tables for pricing and capacity enforcement.

## 2026-04-13 - Introduced BaseEntity and JPA Auditing
Added a shared `BaseEntity` `@MappedSuperclass` with `id`, `createdAt`, and `updatedAt` so every entity has consistent traceability metadata. Enabled Spring Data JPA auditing via `JpaAuditConfig` so timestamps are populated automatically on insert/update, and explicitly configured `modifyOnCreate=true` plus an `Instant`-based `DateTimeProvider` so `updatedAt` is non-null on initial insert and matches the entity field types.

## 2026-04-13 - Modelled Zone Reference Entity (Phase 1)
Added the minimal `Zone` JPA entity (`code`, `name`) mapped to the seeded `zones` table so Ticketing can reference zone identifiers for entitlement modelling without waiting for Phase 2 Park CRUD endpoints.

## 2026-04-13 - Modelled ParkConfiguration Reference Entity (Phase 1)
Added the `ParkConfiguration` JPA entity mapped to the seeded `park_configurations` table so Ticketing can read park-wide capacity settings as data rather than a hardcoded constant. The entity extends `BaseEntity` to inherit auditing timestamps and includes a simple fail-fast constructor check that mirrors the database constraint (`max_daily_capacity > 0`). Updated Phase 1 implementation notes to reflect which minimal Park entities exist in code versus which are planned next.

## 2026-04-13 - Modelled SeasonalPeriod Reference Entity (Phase 1)
Added the `SeasonalPeriod` JPA entity and `SeasonType` enum mapped to the seeded `seasonal_periods` table. This is the Park-owned reference data Ticketing reads during ticket issuance to classify a visit date as PEAK or OFF_PEAK, which drives the price-matrix lookup. Updated Phase 1 implementation notes to reflect that SeasonalPeriod is now modelled in code.

## 2026-04-13 - Modelled PassType Configuration Entity (Phase 1)
Added the `PassType` JPA entity and `PassTypeCode` enum mapped to the seeded `pass_types` table. Pass types are configuration data that define which categories of tickets can be sold (FR-VT1) and will be returned by the planned `GET /api/ticketing/pass-types` endpoint (see `PHASE_01_TICKETING_DESIGN.md`) once implemented. The entity includes basic checks to fail fast on invalid combinations (e.g., `MULTI_DAY` requires a positive multi-day count) and the database schema enforces this invariant via a CHECK constraint.

## 2026-04-13 - Implemented AES-GCM PII Encryption Converter (Phase 1)
Added `StringEncryptionConverter` in `common.converter` to satisfy SE-1 (encrypt visitor PII at rest). The converter implements JPA's `AttributeConverter<String, String>` and is wired as a Spring `@Component` so Hibernate resolves it from the Spring context, enabling `@Value` injection of the AES-256 key. Stored format is `Base64(IV || ciphertext+tag)` with a fresh 12-byte random IV per call, making ciphertexts non-deterministic and tamper-detectable via the GCM authentication tag. Global PII encryption conventions codified in `IMPLEMENTATION.md` §13.

## 2026-04-13 - Modelled ParkDayCapacity and Atomic Increment Repository (Phase 1)
Implemented the `ParkDayCapacity` entity and `ParkDayCapacityRepository.incrementIfCapacityAvailable` guarded update query. This is the concurrency-safe mechanism for FR-VT3 capacity enforcement: the DB atomically increments `issued_count` only while `issued_count < max_capacity`, so concurrent ticketing counters cannot oversell the same day. Added a focused integration test and documented the first-level cache caveat for bulk JPQL updates (clear persistence context before re-read).

## 2026-04-13 - Modelled AccessEntitlement for Ticket Grants (Phase 1)
Implemented `EntitlementType` and `AccessEntitlement` to represent what a ticket grants in a flexible, row-based model (`ZONE`, `RIDE`, `QUEUE_PRIORITY`). Added type-aware validation rules so each entitlement type requires only its relevant field (`zoneId`, `rideId`, or `priorityLevel`) and rejects invalid combinations. Kept `zoneId` and `rideId` as scalar IDs (not JPA relationships) to enforce the cross-context boundary rule from `DESIGN.md` §6.2.

## 2026-04-13 - Modelled Visit Entity and RFID Resolution Query (Phase 1)
Implemented the `VisitStatus` enum and `Visit` entity to represent an active session in the park. Added `VisitRepository` with the `findActiveByRfidTag` query. This query implements the PR-1 hot path (resolving an RFID scan to a visitor and their ticket entitlements) using an index-only lookup against the `idx_visits_wristband_status` index and `JOIN FETCH` to eagerly hydrate the associated `Visitor` and `Ticket` entities in a single database round-trip.

## 2026-04-13 - Modelled Ticket Entity (Phase 1)
Added the `Ticket` entity to represent an immutable purchase record. Configured JPA `@ManyToOne` relationships to `Visitor` and `PassType` since these reside within the same Ticketing context (conforming to `DESIGN.md` §6.2 which allows intra-context relationships but forbids cross-context ones). Storing `pricePaidCents` and `currency` implements the price snapshot pattern so historical records are not affected by future configuration changes.

## 2026-04-13 - Modelled Wristband Entity (Phase 1)
Implemented the `WristbandStatus` enum and `Wristband` entity. The `Wristband` encapsulates state transition rules (`activate()`, `returnToStock()`, `deactivate()`) enforcing that it must be `IN_STOCK` to activate. Created `WristbandRepository` to look up wristbands by their unique RFID tag, which supports the PR-1 hot path.

## 2026-04-13 - Modelled PassTypePrice Entity and Pricing Enums (Phase 1)
Implemented the `AgeGroup` and `DayType` enums, and the `PassTypePrice` entity mapped to `pass_type_prices`. This represents the explicit pricing matrix (FR-VT2) rather than using hardcoded calculation formulas, making pricing auditable and configurable. Created `PassTypePriceRepository` with a unique combination lookup query `findByPassTypeAndAgeGroupAndDayTypeAndSeasonType`.

## 2026-04-13 - Refactored SeasonType to common (Phase 1)
Moved `SeasonType` from `park.entity` to `common.entity` because it is a shared enum primitive used by both `SeasonalPeriod` (Park context) and `PassTypePrice` (Ticketing context).

## 2026-04-13 - Added Park Reference Service for Ticketing JIT (Phase 1)
Implemented `ParkConfigurationRepository`, `SeasonalPeriodRepository`, and `ParkReferenceService` in the `park` context. This provides just-in-time read access for the Ticketing context to resolve the active park configuration (for daily capacity limits) and the `SeasonType` for a given date (for pricing). Consistent with cross-context rules, Ticketing will call this service rather than accessing Park repositories directly.

## 2026-04-13 - Implemented Visitor Registration Endpoint (Phase 1)
Implemented `POST /api/ticketing/visitors` end-to-end: `VisitorRepository`, `VisitorService`, `CreateVisitorRequest` (Bean Validation DTO), `VisitorResponse`, and `VisitorController` (returns 201). Tests written at two levels: `VisitorControllerTest` uses `@WebMvcTest` + `@MockitoBean` to test validation and HTTP mapping in isolation; `VisitorIntegrationTest` uses `@SpringBootTest` + `@Transactional` to verify the full registration flow including PII encryption round-trip against the in-memory H2 database.

## 2026-04-13 - Modelled Visitor Entity With Encrypted PII (Phase 1)
Added the `Visitor` JPA entity mapped to the `visitors` table. PII fields (firstName, lastName, email, phone) use `@Convert(converter = StringEncryptionConverter.class)` so Hibernate transparently encrypts them on write and decrypts on read; the `_enc` column suffix signals this to anyone reading the schema. Non-PII operational fields (dateOfBirth, heightCm) are stored plain for pricing and eligibility hot-path performance. All business invariants (non-blank names, non-null/non-future dateOfBirth using explicit UTC, positive heightCm) are enforced in both the constructor and `@PrePersist`/`@PreUpdate` lifecycle callbacks.

## 2026-04-13 - Implemented PassType Configuration Endpoint (Phase 1)
Implemented the `GET /api/ticketing/pass-types` endpoint returning active pass types ordered by code.
- Added `PassTypeRepository` (read-only Spring Data repository)
- Added `PassTypeService` (read-only transaction)
- Added `PassTypeResponse` (record DTO)
- Added `PassTypeController` (delegates to service)
- Wrote full-context integration test using `@SpringBootTest` and `@AutoConfigureMockMvc` (using Boot 4's `spring-boot-starter-webmvc-test` module) to verify end-to-end reading from the Flyway-seeded H2 database.

## 2026-04-13 - Clarified Phase 1.1 Visit Issuance Rules (Docs)
Documented the explicit Phase 1.1 issuance decisions (defaults, invariants, and edge cases) as a pre-implementation checklist so behavior is explainable and testable before `POST /api/ticketing/visits` is implemented.

## 2026-04-14 - Implemented Visit Issuance Endpoint (Phase 1)
Implemented `POST /api/ticketing/visits` end-to-end to sell a ticket, associate/auto-create an RFID wristband, reserve daily capacity, and start an ACTIVE visit atomically. Added pessimistic locking repository methods to serialize concurrent issuance attempts and hardened the flow against unique-constraint races when creating missing wristbands or capacity rows. Added controller slice tests for validation and exception-to-HTTP mapping, plus an integration test that verifies the full persistence flow against Flyway-seeded H2.

## 2026-04-14 - Implemented RFID Resolution Debug Endpoint (Phase 1)
Implemented `GET /api/ticketing/rfid/{rfidTag}/active-visit` to resolve an RFID tag to the currently ACTIVE visit and return the scan-hot-path attributes needed by downstream contexts. Added controller and integration coverage for success, 404 (`ACTIVE_VISIT_NOT_FOUND`), and invalid path-variable validation, and extended global exception handling to map `ConstraintViolationException` to the standard 400 `VALIDATION_FAILED` contract.

## 2026-04-14 - Synced Phase 1 Implementation Notes With Park/Ticketing Reality
Updated the Phase 1 implementation notes to reflect already-implemented Park reference access (`ParkReferenceService`) and the shared `SeasonType` location in `common.entity`, removing stale "planned" wording.

## 2026-04-15 - Implemented Phase 1.2 Multi-Day Ticket Issuance
Removed the Phase 1.1 MULTI_DAY rejection guard and implemented full multi-day issuance: validity window is fixed at purchase time (validFrom = visitDate, validTo = visitDate + multiDayCount - 1, both immutable), and capacity is reserved atomically for every date in the window inside a single transaction — if any day is sold out the whole transaction rolls back (all-or-nothing). Also introduced the INACTIVE wristband status (V003 migration) to correctly represent wristbands that are physically on a visitor's wrist between multi-day visit sessions, distinct from IN_STOCK (unclaimed stockroom) and DEACTIVATED (retired). Tests cover validity window, per-day capacity reservation, all-or-nothing rollback (non-transactional test with committed setup), and the INACTIVE wristband rejection guard.

## 2026-04-15 - Designed Phase 1.3 Entitlement Creation Rules
Specified Phase 1.3 entitlement creation rules in the Ticketing slice design: persist `AccessEntitlement` rows as an immutable snapshot at ticket issuance so scan-processing contexts can resolve RFID to a stable entitlement set without recomputing it. Defined Phase 1 semantics: full park access is represented as one `ZONE` entitlement per seeded zone, and `FAST_TRACK` additionally grants one `QUEUE_PRIORITY` entitlement with `priorityLevel = 2`. Documented a temporary Phase 1 behavior where `RIDE_SPECIFIC` is treated as zone-only for stored entitlements until Phase 6 introduces authoritative ride reference data.

## 2026-04-15 - Implemented Phase 1.3 Entitlement Creation At Issuance
Extended `VisitService.issueTicketAndStartVisit` to persist `AccessEntitlement` rows in the same transaction as `Ticket` creation so the RFID resolution response reflects the contractual entitlements snapshot. Implemented Phase 1 semantics by creating one `ZONE` entitlement per seeded zone for all Phase 1 pass types, plus one `QUEUE_PRIORITY` entitlement with `priorityLevel = 2` for `FAST_TRACK`. Added service and integration test coverage that asserts entitlements are created and returned by the debug RFID resolution endpoint.

## 2026-04-15 - Hardened RFID/Entitlement Hot Path (Phase 1.3)
Hardened the scan hot path by eager-fetching `Ticket.passType` during RFID resolution to avoid an extra lazy-load query per scan. Added a deterministic ordering for entitlement reads and added a Flyway V004 migration that indexes `access_entitlements.ticket_id` plus enforces type-specific field invariants via a database CHECK (defense in depth). Also aligned seed text for `RIDE_SPECIFIC` so Phase 1 observable behavior matches the documented temporary semantics.

## 2026-04-15 - Designed and Reviewed Phase 2 (Park CRUD)
Created Phase 2 design (`PHASE_02_PARK_DESIGN.md`) and implementation plan (`PHASE_02_PARK_IMPLEMENTATION.md`) covering Zone CRUD, ParkConfiguration management, and SeasonalPeriod management. Ran an adversarial subagent review that surfaced several blocking issues: capacity change propagation to existing `park_day_capacity` rows, concurrency races on config/period writes (fixed with a dedicated `park_write_lock` single-row table), and semantic ambiguity around `Zone.active` (resolved as an operational open/closed flag, not an issuance filter). All design decisions recorded with rationale before implementation began.

## 2026-04-15 - Implemented Phase 2.2 ParkConfiguration Management
Implemented ParkConfiguration management endpoints (`GET /api/park/configurations`, `GET /api/park/configurations/active`, `POST /api/park/configurations`). Creating a new configuration atomically deactivates the previous one (via JPQL bulk update) and activates the new one -- all within a single transaction under the `park_write_lock`. Capacity reduction is validated against existing `park_day_capacity` rows for today and future dates; if any date's `issued_count` exceeds the new max, the operation is rejected with 422 `CAPACITY_REDUCTION_CONFLICT`. The known race between the conflict check and concurrent ticket issuance is documented as an accepted limitation in the design doc. `entityManager.clear()` is called after JPQL bulk updates to prevent first-level cache staleness. Integration tests cover activation (old deactivated, new active), capacity increase, safe reduction, conflicting reduction (atomicity -- no config row created on failure), and earliest-conflict-date in error message.

## 2026-04-15 - Implemented Phase 2.1 Zone CRUD
Implemented full Zone management endpoints (`GET`, `POST`, `PUT /api/park/zones[/{id}]`). V005 adds `description` and `active` columns to `zones`; V006 introduces the `park_write_lock` concurrency anchor table (used by later sub-slices). Zone code is immutable after creation (`updatable=false` at the JPA layer). No hard-delete endpoint: zones are deactivated (`active=false`) rather than deleted, because zone IDs are referenced as plain integers across future bounded contexts. TOCTOU concurrency on code uniqueness handled by catching `DataIntegrityViolationException` after the pre-check. `saveAndFlush` used on update to ensure JPA auditing populates `updatedAt` before the response DTO is built. H2 multi-column `ALTER TABLE` limitation documented in `IMPLEMENTATION.md`.

## 2026-04-15 - Implemented Phase 2.3 SeasonalPeriod Management
Implemented SeasonalPeriod management endpoints (`GET /api/park/seasonal-periods`, `GET /api/park/seasonal-periods/{id}`, `POST /api/park/seasonal-periods`, `DELETE /api/park/seasonal-periods/{id}`) with explicit domain-rule errors: 422 for invalid date ranges and 422 for overlap conflicts. Writes (create/delete) acquire the `park_write_lock` pessimistically to serialize admin operations and prevent TOCTOU overlap races, while the ticket-pricing read path remains lock-free. Added controller and integration tests to cover list/get/create/delete flows plus error mapping, and aligned Phase 2 implementation notes with the H2-safe V005 migration semantics.

## 2026-04-15 - Hardened Phase 2.3 SeasonalPeriod Error Contract And Overlap Boundary Tests
Aligned the Phase 2 Park design contract with actual Spring/Jackson behavior: an unknown `seasonType` enum value fails during JSON deserialization and is surfaced as 400 `MALFORMED_JSON` (not `VALIDATION_FAILED`). Strengthened controller tests to assert this error code so future changes can’t silently drift the API contract. Added explicit integration coverage for inclusive date-range overlap boundaries (sharing an endpoint day is a conflict; starting the day after is allowed).

## 2026-04-15 - Eliminated Remaining Phase 2 SeasonalPeriod Doc Drift
Updated the Phase 2 invariants/validation summary to correctly distinguish deserialization errors (unknown enum values) from Bean Validation failures (missing fields), keeping the design doc consistent with the global `GlobalExceptionHandler` behavior. Tightened the controller test to assert the full standard error envelope and to prove the service is not invoked when JSON deserialization fails.
