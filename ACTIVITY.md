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

## 2026-04-13 - Modelled Visitor Entity With Encrypted PII (Phase 1)
Added the `Visitor` JPA entity mapped to the `visitors` table. PII fields (firstName, lastName, email, phone) use `@Convert(converter = StringEncryptionConverter.class)` so Hibernate transparently encrypts them on write and decrypts on read; the `_enc` column suffix signals this to anyone reading the schema. Non-PII operational fields (dateOfBirth, heightCm) are stored plain for pricing and eligibility hot-path performance. All business invariants (non-blank names, non-null/non-future dateOfBirth using explicit UTC, positive heightCm) are enforced in both the constructor and `@PrePersist`/`@PreUpdate` lifecycle callbacks.

## 2026-04-13 - Implemented PassType Configuration Endpoint (Phase 1)
Implemented the `GET /api/ticketing/pass-types` endpoint returning active pass types ordered by code.
- Added `PassTypeRepository` (read-only Spring Data repository)
- Added `PassTypeService` (read-only transaction)
- Added `PassTypeResponse` (record DTO)
- Added `PassTypeController` (delegates to service)
- Wrote full-context integration test using `@SpringBootTest` and `@AutoConfigureMockMvc` (using Boot 4's `spring-boot-starter-webmvc-test` module) to verify end-to-end reading from the Flyway-seeded H2 database.
