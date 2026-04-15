# ATPEZMS Phase 2 (Park) -- Detailed Design

This document is the **Level 2 (vertical slice) Detailed Design** for **Phase 2: Park**, as required by `DESIGN_RULES.md`.

It refines the Park section of the global blueprint (`DESIGN.md §5.1`) into an implementable design: exact domain model, schema additions, REST contracts, invariants, and key trade-offs.

Implementation mechanics (Spring annotations, class names, transaction wiring, Flyway file names) are specified in `PHASE_02_PARK_IMPLEMENTATION.md`.

---

## 1. Scope

### 1.1 Primary Goal

Implement the management APIs for the **Park** bounded context: Zones, ParkConfigurations, and SeasonalPeriods.

In Phase 1, Park data was seeded by Flyway as static reference data. Phase 2 replaces that static approach with real management endpoints so park operators can update the park structure and configuration without a database migration.

### 1.2 Functional Requirements Covered

- **FR-VT2** (seasonal pricing reference) -- SeasonalPeriod CRUD enables pricing calendar changes at runtime.
- **FR-VT3** (capacity configuration) -- ParkConfiguration management enables capacity limit changes at runtime.
- **SC-1** (adding new zones requires no architectural changes) -- Zone CRUD enables zones to be added without a code deployment.
- **DESIGN.md §3.6** (configuration as data) -- All operational park settings are database records, not application properties.

### 1.3 Explicit Non-Goals For Phase 2

- Authentication/authorization enforcement (Phase 3). All endpoints are designed for security but enforcement is deferred.
- Ride, Food, Merchandise location management (those contexts reference Zone by ID; they manage their own entities in later phases).
- Zone entry/exit enforcement at scan time (Phase 6 Rides).
- Analytics/reporting on park data (Phase 12).
- `PassType`/`PassTypePrice` management APIs (owned by the Ticketing context; deferred to a Ticketing admin sub-slice).
- Actor audit trail (`createdBy`/`updatedBy`) deferred to Phase 3 when the Identity context provides a populated `AuditorAware`. Until then the fields do not exist.

---

## 2. Key Decisions (With Rationale)

### 2.1 Additive Schema Changes Only

The Phase 1 Park schema (`zones`, `park_configurations`, `seasonal_periods`) is already in production-grade shape. Phase 2 only adds:
- Two new columns to `zones` (`description`, `active`) via a V005 Flyway migration.
- One new table `park_write_lock` (single-row concurrency anchor, §2.4).
- No structural changes to `park_configurations` or `seasonal_periods`.

Rationale: both zone columns are additive (`description` is nullable, `active` defaults to `TRUE`), so existing code and tests continue to work unchanged.

### 2.2 Zone `active` = Operational Open/Closed Flag

`active = false` means the zone is **physically closed** right now (e.g., under renovation). It is a runtime operational state, not an issuance flag.

Consequences:
- **Entitlements are still issued for all zones at ticket purchase**, regardless of `active`. A visitor who buys a full-park pass gets entitlements for every zone -- they paid for it. The temporary closure of one zone does not change what was purchased.
- **Phase 6 (Rides) will enforce `active` at scan time**: when a visitor's wristband is scanned at a zone-restricted entry point, the zone's `active` status is checked and entry is denied if the zone is closed, even if the entitlement exists.
- `ParkReferenceService.listZoneIds()` is **not** changed in Phase 2. It continues to return all zone IDs for entitlement creation.

Why not use `active` as an issuance filter? It would silently shrink what a visitor paid for when a zone closes mid-day (visitors issued tickets before the change would have different entitlements than those issued after). That asymmetry is confusing and unfair. The correct operational model is: sell full-park access always, deny physical entry to closed zones at the gate.

### 2.3 "Exactly One Active ParkConfiguration" -- Activation-On-Create Pattern

Creating a new `ParkConfiguration` always activates it immediately. There is no "create inactive, activate later" flow because partial state (new config exists but neither active) is indeterminate.

The endpoint `POST /api/park/configurations` atomically:
1. Locks the concurrency anchor (§2.4).
2. Deactivates the current active configuration.
3. Creates the new configuration with `active = true`.
4. Propagates the new `maxDailyCapacity` to affected `park_day_capacity` rows (§2.5).

All four steps occur in one transaction. There is no standalone activate/deactivate endpoint.

Configuration records are **append-only**. You cannot update or delete a past configuration. Each `POST` creates a new version and deactivates the previous one. This keeps the full history of capacity decisions visible and auditable, consistent with DESIGN.md §3.8.

### 2.4 Concurrency Safety -- Single-Row Lock Table

Both `park_configurations` and `seasonal_periods` require serialized writes to maintain their invariants (single-active config; non-overlapping periods). Two concurrent admin requests could both read consistent state, both pass validation, and both commit in violation of the invariant.

**Solution:** a `park_write_lock` table seeded with exactly one row. Any write to `park_configurations` or `seasonal_periods` must `SELECT ... FOR UPDATE` that row first. This pessimistic lock serializes all park configuration writes through a single bottleneck.

Why a dedicated lock table instead of locking an existing row? Because `seasonal_periods` can be empty (e.g., all periods deleted), so there is no anchor row to lock. A purpose-built lock table always has the anchor row.

Why is this acceptable? Park configuration writes are rare admin operations (park managers don't change capacity daily). The serialization overhead is irrelevant.

### 2.5 Capacity Change Propagation to `park_day_capacity`

When a new `ParkConfiguration` is activated with a changed `maxDailyCapacity`, existing `park_day_capacity` rows for **today and future dates** must be updated to reflect the new limit.

Rules, executed atomically in the same transaction as config activation:
1. Find all `park_day_capacity` rows where `visit_date >= today (UTC)`.
2. If **any** such row has `issued_count > newMaxDailyCapacity`, reject the whole operation with **422 `CAPACITY_REDUCTION_CONFLICT`**. The error response must include the earliest conflicting date so the operator knows what to address.
3. If none conflict, update all affected rows: `SET max_capacity = newMaxDailyCapacity`.

Why? An operator who sets a new capacity expects it to take effect for the current operational period, not only for dates that haven't been initialized yet. A capacity increase (common case) is always safe. A capacity reduction is allowed unless it would require "un-issuing" already-sold tickets, which is impossible.

**Known limitation (acceptable for this phase):** The conflict check (`SELECT` conflicting rows) and the bulk update (`UPDATE max_capacity`) are two separate database statements. In theory, a concurrent ticket issuance could increment `issued_count` on a `park_day_capacity` row in the window between these two statements, allowing the update to commit with `max_capacity < issued_count` for that row. In practice this race requires simultaneous admin capacity changes and ticket issuance at high volume on specific future dates -- an operationally unlikely scenario. The `park_write_lock` serializes concurrent *config activation* requests against each other but does not block concurrent ticket issuance. A production-grade fix would lock the affected `park_day_capacity` rows pessimistically before the check. This is deferred; the limitation is documented here so it is not forgotten.

### 2.6 SeasonalPeriod Immutability

SeasonalPeriods are immutable once created. If a period is wrong, delete it and create a new one. No `PUT`/`PATCH` endpoint.

Why immutable? Seasonal periods affect ticket pricing. A ticket purchased during a PEAK period was priced at PEAK rates. Retroactively editing that period would misrepresent why the price was what it was. Future-dated periods can be deleted and recreated freely (no pricing has been decided against them yet) -- but the API does not enforce this distinction. A simple delete is allowed on any period.

### 2.7 Zone Hard-Delete Removed

There is no `DELETE /api/park/zones/{id}` endpoint. The only supported "removal" operation is setting `active = false` via `PUT /api/park/zones/{id}`.

Why? Zone IDs are referenced as plain `BIGINT` columns across multiple future contexts (`access_entitlements.zone_id`, and future `rides`, `food`, `merchandise`, `events` tables). These contexts store the ID without a database foreign-key constraint (DESIGN.md §6.2 cross-context boundary rule). A hard-delete would produce dangling integer references with no DB integrity check to catch them. Deactivation preserves the record while signaling the zone is no longer operational.

---

## 3. Domain Model

### 3.1 Zone (Extended)

Phase 1 Zone only had `code` and `name`. Phase 2 adds:

| Field | Type | Notes |
|---|---|---|
| `description` | VARCHAR(500), nullable | Human-readable description for staff dashboards. |
| `active` | BOOLEAN, NOT NULL, DEFAULT TRUE | Operational open/closed flag. See §2.2. |

The `code` field is immutable after creation. It is the stable application-level human identifier. Changing a code after zones have been referenced (in logs, documentation, other systems) would cause confusion and cross-context inconsistency.

### 3.2 ParkConfiguration (Unchanged Schema)

No new columns. Existing schema (`active`, `max_daily_capacity`) is sufficient. Phase 2 adds the management API only.

### 3.3 SeasonalPeriod (Unchanged Schema)

No new columns. Existing schema (`start_date`, `end_date`, `season_type`) is sufficient. Phase 2 adds the management API (create, list, get, delete). No update endpoint (see §2.6).

---

## 4. REST API

Base path: `/api/park`

All success responses return DTOs directly. Error responses follow the global `ErrorResponse` format (`IMPLEMENTATION.md §4`).

Role notes apply from Phase 3 onward. Before Phase 3, all endpoints are open. Controller methods carry a `// Requires: <role>` comment (`IMPLEMENTATION.md §8`).

### 4.1 Zone Endpoints

#### `GET /api/park/zones`

- Purpose: list all zones, ordered by `code` ascending.
- Query parameter: `activeOnly` (boolean, optional, default `false`). When `true`, returns only zones where `active = true`.
- Requires (future): `ROLE_MANAGER` or `ROLE_ADMIN`.
- Response: list of zone response DTOs. 200 OK.

Response DTO fields per zone: `id`, `code`, `name`, `description`, `active`, `createdAt`, `updatedAt`.

#### `GET /api/park/zones/{id}`

- Purpose: get a single zone by ID.
- Requires (future): `ROLE_MANAGER` or `ROLE_ADMIN`.
- Failure: 404 `ZONE_NOT_FOUND`.
- Success: 200 OK.

#### `POST /api/park/zones`

- Purpose: create a new zone.
- Requires (future): `ROLE_ADMIN` or `ROLE_MANAGER`.
- Request fields:
  - `code` (required, 1–50 chars, must match `[A-Z0-9_]+`)
  - `name` (required, 1–100 chars)
  - `description` (optional, max 500 chars)
  - `active` (optional, default `true`)
- Failure cases:
  - 409 `ZONE_CODE_ALREADY_EXISTS` -- a zone with this code already exists.
  - 400 `VALIDATION_FAILED` -- missing required fields, bad format, code fails regex.
- Success: 201 Created with the created zone DTO.

Why restrict `code` to `[A-Z0-9_]+`? The code is a stable application-level identifier used in documentation, logs, and potentially in JSON payloads sent by operators. Allowing spaces, dashes, or locale-sensitive characters would create ambiguity. The existing seed codes (`ADVENTURE`, `WATER`, etc.) already follow this pattern -- this constraint formalizes the convention.

#### `PUT /api/park/zones/{id}`

- Purpose: update a zone's mutable fields: name, description, and active status.
- `code` is NOT updatable (see §3.1). If `code` is included in the request body it is ignored (or rejected with a 400 -- implementation decision; document in `PHASE_02_PARK_IMPLEMENTATION.md`).
- Requires (future): `ROLE_ADMIN` or `ROLE_MANAGER`.
- Request fields:
  - `name` (required, 1–100 chars)
  - `description` (optional, max 500 chars; omitting sets it to `null`)
  - `active` (required, boolean)
- Failure: 404 `ZONE_NOT_FOUND`.
- Success: 200 OK with the updated zone DTO.

`PUT` is a full replacement of mutable fields. Omitting `description` explicitly sets it to `null`. This is consistent with `PUT` semantics (full update). Clients that want to keep the existing description must re-send it.

Note on deactivating a zone with issued entitlements: allowed. Historical entitlements are immutable snapshots and are not revoked. The zone is now physically closed; Phase 6 will enforce this at scan time.

### 4.2 ParkConfiguration Endpoints

#### `GET /api/park/configurations`

- Purpose: list all park configurations (current and historical), ordered by `id` descending (newest first).
- Requires (future): `ROLE_MANAGER` or `ROLE_ADMIN`.
- Response: list of configuration DTOs. 200 OK.

Response DTO fields per config: `id`, `active`, `maxDailyCapacity`, `createdAt`, `updatedAt`.

#### `GET /api/park/configurations/active`

- Purpose: get the currently active park configuration.
- Requires (future): `ROLE_MANAGER`, `ROLE_ADMIN`, or `ROLE_TICKET_STAFF`.
- Failure: 404 `NO_ACTIVE_PARK_CONFIGURATION` (should not happen in a correctly seeded system, but the endpoint must be defensive).
- Success: 200 OK.

#### `POST /api/park/configurations`

- Purpose: create a new park configuration and atomically activate it (see §2.3 and §2.5).
- Requires (future): `ROLE_ADMIN` or `ROLE_MANAGER`.
- Request fields:
  - `maxDailyCapacity` (required; Bean Validation: `@Min(1)`)
- Success: 201 Created with the new active configuration DTO.
- Failure cases:
  - 400 `VALIDATION_FAILED` -- `maxDailyCapacity` is missing or < 1 (Bean Validation).
  - 422 `CAPACITY_REDUCTION_CONFLICT` -- reducing capacity below the already-issued count for one or more future dates. The error message must include the earliest conflicting date.

### 4.3 SeasonalPeriod Endpoints

#### `GET /api/park/seasonal-periods`

- Purpose: list all seasonal periods, ordered by `start_date` ascending.
- Requires (future): `ROLE_MANAGER` or `ROLE_ADMIN`.
- Response: list of period DTOs. 200 OK.

Response DTO fields per period: `id`, `startDate`, `endDate`, `seasonType`, `createdAt`, `updatedAt`.

#### `GET /api/park/seasonal-periods/{id}`

- Purpose: get a single period.
- Requires (future): `ROLE_MANAGER` or `ROLE_ADMIN`.
- Failure: 404 `SEASONAL_PERIOD_NOT_FOUND`.
- Success: 200 OK.

#### `POST /api/park/seasonal-periods`

- Purpose: create a new seasonal period.
- Requires (future): `ROLE_ADMIN` or `ROLE_MANAGER`.
- Request fields:
  - `startDate` (required, ISO date `YYYY-MM-DD`; Bean Validation: `@NotNull`)
  - `endDate` (required, ISO date `YYYY-MM-DD`; Bean Validation: `@NotNull`)
  - `seasonType` (required, `PEAK` or `OFF_PEAK`; Bean Validation: `@NotNull`)
- Failure cases:
  - 422 `SEASONAL_PERIOD_INVALID_DATES` -- `endDate < startDate` (service-layer check, after validation).
  - 422 `SEASONAL_PERIOD_DATE_CONFLICT` -- the proposed range overlaps an existing period.
  - 400 `VALIDATION_FAILED` -- missing required fields.
  - 400 `MALFORMED_JSON` -- unknown `seasonType` value (JSON deserialization/type mismatch).
- Success: 201 Created.

Why is `endDate < startDate` a 422 (business rule) rather than 400 (validation)? The dates themselves are individually valid ISO dates, so the Bean Validation layer accepts them. The constraint "end must not precede start" is a cross-field domain rule, which by DESIGN.md §3.2 convention maps to 422.

#### `DELETE /api/park/seasonal-periods/{id}`

- Purpose: delete a seasonal period.
- Requires (future): `ROLE_ADMIN` or `ROLE_MANAGER`.
- Failure: 404 `SEASONAL_PERIOD_NOT_FOUND`.
- Success: 204 No Content.

No update endpoint (immutability rationale in §2.6).

---

## 5. ParkReferenceService (No Changes in Phase 2)

`ParkReferenceService.listZoneIds()` continues to return all zone IDs, regardless of `active` status. New tickets will include `ZONE` entitlements for every zone, including currently-closed ones, because the visitor paid for full-park access. Phase 6 enforces operational closure at scan time.

---

## 6. Data Model Changes (Schema Delta)

Two migrations are needed.

### V005: Add `description` and `active` to `zones`

```sql
ALTER TABLE zones
    ADD COLUMN description VARCHAR(500),
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
```

Both columns are additive. `description` is nullable. `active` defaults to `TRUE` so all existing seeded zones remain active after migration.

### V006: Add `park_write_lock` anchor table

```sql
CREATE TABLE park_write_lock (
    id         INT         NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    CONSTRAINT pk_park_write_lock PRIMARY KEY (id)
);

INSERT INTO park_write_lock (id, created_at) VALUES (1, CURRENT_TIMESTAMP);
```

Purpose: single-row table used for pessimistic locking to serialize concurrent writes to `park_configurations` and `seasonal_periods`. Always contains exactly one row with `id = 1`.

---

## 7. Invariants and Validation Summary

| Entity | Invariant | Enforcement |
|---|---|---|
| Zone | `code` unique | DB UNIQUE constraint (exists from V001) |
| Zone | `code` format: `[A-Z0-9_]+`, 1–50 chars | Service-layer validation (400 on failure) |
| Zone | `code` immutable after creation | Service layer (PUT request does not include code as updatable field) |
| Zone | `name` not blank, 1–100 chars | Bean Validation on request DTO (400 on failure) |
| Zone | No hard-delete | No DELETE endpoint; use `active=false` instead |
| ParkConfiguration | Exactly one `active = true` row | Service layer: lock anchor → deactivate old → create new (atomically) |
| ParkConfiguration | `maxDailyCapacity >= 1` | Bean Validation on request DTO (400 on failure) |
| ParkConfiguration | Capacity reduction must not undershoot already-issued counts | Service layer: check `park_day_capacity` rows for today+future (422 on conflict) |
| ParkConfiguration | Records are append-only | No PUT/PATCH/DELETE endpoints |
| SeasonalPeriod | `end_date >= start_date` | DB CHECK (V001) + service-layer cross-field check (422 on failure) |
| SeasonalPeriod | `season_type` is `PEAK` or `OFF_PEAK` | DB CHECK (V001) + Bean Validation enum (400 on failure) |
| SeasonalPeriod | No overlapping date ranges | Service layer with pessimistic lock on `park_write_lock` (422 on conflict) |
| SeasonalPeriod | Immutable after creation | No PUT/PATCH endpoint |
| park_write_lock | Always contains exactly one row (`id = 1`) | V006 seed; application never writes to this table except to acquire the lock |

---

## 8. Cross-Context Interactions

**Outgoing from Park:**
- None. Park is a provider of reference data; it does not call other contexts.

**Incoming to Park (unchanged from Phase 1):**
- Ticketing reads zone IDs, active capacity, and season type via `ParkReferenceService`.

**Impact on other contexts:**
- No behavioral change to `ParkReferenceService` in Phase 2. Existing consumers (`VisitService`) are unaffected.

---

## 9. Phase 2 Internal Sub-Phasing

Phase 2 is implemented in three sub-slices. Each follows: implementation → verification → adversarial review → commit.

1. **Phase 2.1 -- Zone CRUD:** V005 + V006 migrations, update `Zone` entity (`description`, `active`), new exceptions (`ZoneNotFoundException`, `ZoneCodeAlreadyExistsException`), Zone service (`ZoneService`), Zone DTOs (`CreateZoneRequest`, `UpdateZoneRequest`, `ZoneResponse`), Zone controller (`ZoneController`).
2. **Phase 2.2 -- ParkConfiguration management:** `ParkConfigurationService` with activate-on-create + capacity propagation, `ParkConfigurationController`, DTOs.
3. **Phase 2.3 -- SeasonalPeriod management:** `SeasonalPeriodService` with overlap check + pessimistic lock, `SeasonalPeriodController`, DTOs.

Each sub-slice is one atomic commit.

---

## 10. Security Readiness

All endpoints are designed for future security enforcement. Each endpoint's required role is noted in Section 4.

- **Write access:** `ROLE_ADMIN` or `ROLE_MANAGER` (both can manage park configuration, per SE-2 which restricts administrative functions to verified Manager role, with Admins getting a superset).
- **Read access:** `ROLE_MANAGER` or `ROLE_ADMIN`; the `GET /api/park/configurations/active` endpoint is also accessible to `ROLE_TICKET_STAFF` (ticket staff need to see current capacity).

Before Phase 3 (Identity + Security), all endpoints are open. Controller methods carry a `// Requires: <role>` comment, per `IMPLEMENTATION.md §8`. When the security skeleton lands in Phase 3, these become `@PreAuthorize` annotations.
