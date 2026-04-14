# ATPEZMS Phase 1 (Ticketing) - Detailed Design

This document is the **Level 2 (vertical slice) Detailed Design** for **Phase 1: Ticketing**, as required by `DESIGN_RULES.md`.

It refines the Ticketing section of the global blueprint (`DESIGN.md`) into an implementable design: exact domain model, database schema, REST contracts, invariants, and key trade-offs.

Implementation mechanics (Spring annotations, class names, transaction wiring, Flyway file names) are specified in `PHASE_01_TICKETING_IMPLEMENTATION.md`.

---

## 1. Scope

### 1.1 Primary Goal

Implement the backend API for **visitor registration and ticket issuance** under the cashless, post-paid RFID wristband model.

The output of the registration flow is:

- a persisted `Visitor` (person record)
- a persisted `Wristband` (RFID tag record)
- a persisted `Ticket` (purchase record with price snapshot)
- a started `Visit` (entry-to-exit session) associated to the wristband

This Phase also establishes the **RFID resolution contract** that other contexts will call on the scan hot path (Rides, Food, Merchandise, Events).

### 1.2 Functional Requirements Covered

- FR-VT1 Pass Types
- FR-VT2 Pricing (age group, weekday/weekend, peak/off-peak)
- FR-VT3 Capacity Enforcement (sold out at max daily visitor capacity)
- FR-VT4 RFID Association (map unique RFID tag to visitor profile)

Note on FR-VT1:

- Phase 1.1 supports single-day pass issuance first.
- Multi-day passes are explicitly designed here, but issuance is deferred to a later mini-slice (Section 9).

### 1.3 Constraints And NFRs Addressed

- CO-1 RFID wristband is the sole identifier for visitor interactions
- CO-3 + SE-1 Encrypt visitor PII at rest
- PR-1 Scan to decision under 1 second (designing for a fast RFID lookup)

### 1.4 Explicit Non-Goals For Phase 1

- Billing ledger and checkout settlement (Phase 4 Billing)
- Ride eligibility enforcement (Phase 6 Rides)
- Telemetry scan event logging and duplicate RFID detection (Phase 5 Telemetry)
- Authentication/authorization enforcement (Phase 3 Identity + Security skeleton)

We still design Phase 1 to be compatible with these later slices.

---

## 2. Key Decisions (With Rationale)

### 2.1 Park Data Is Treated As Seeded Reference Data (For Phase 1 Only)

Ticketing pricing and capacity enforcement require park-wide configuration and season classification. We will therefore create and seed a **minimal Park reference schema** via Flyway during Phase 1:

- zones
- park configuration (max daily capacity)
- seasonal calendar (peak/off-peak periods)

Rationale:

- It avoids blocking Ticketing behind full Park CRUD endpoints.
- It keeps the global rule "configuration as data" true from the start (no hardcoded max capacity).
- It lets Ticketing calculate price deterministically and enforce capacity correctly.

Park management APIs (CRUD + audit) remain Phase 2.

### 2.2 Ticket Issuance Starts A Visit

When a ticket is sold and a wristband is issued, we create an **ACTIVE Visit** immediately.

Rationale:

- It matches the operational workflow in the spec: issuance happens at entry.
- It makes RFID resolution trivial and fast: "RFID tag -> active Visit".
- It gives a stable Visit ID that later contexts can attach charges and scans to.

### 2.3 Pricing Uses A Stored Price Matrix, Not Hardcoded Formulas

Pricing variability (age group, weekday/weekend, peak/off-peak) is implemented via a `pass_type_prices` table that stores the explicit price for each combination.

Rationale:

- It is easy to explain and audit.
- It is easy to change without code changes.
- It avoids subtle bugs from complex pricing formulas.

### 2.4 Capacity Enforcement Uses A Per-Day Counter Row

We enforce daily capacity using a `park_day_capacity` table and an atomic "increment-if-not-full" update.

Rationale:

- Counting visits (`SELECT COUNT(*)`) is not safe under concurrency.
- A guarded update provides a single database-enforced point of truth.

---

## 3. Domain Concepts

### 3.1 Definitions

- **Visitor**: a person record. May return in future visits.
- **Wristband**: a physical RFID tag that is the visitor's identifier while in the park.
- **Ticket**: the purchase record for a pass type, with a price snapshot.
- **PassType**: a configuration record describing a pass category.
- **Visit**: one entry-to-exit session in the park.
- **AccessEntitlement**: what the Ticket grants (zones/rides/priority). In Phase 1 we store entitlements but do not yet have Rides enforcement.

### 3.2 Visitor PII Classification

PII (must be encrypted at rest per SE-1):

- full legal name (first/last)
- email
- phone number

Non-PII operational attributes (not encrypted to keep eligibility/pricing lookups simple):

- heightCm
- dateOfBirth (decision: store plain or derived age)

Decision for Phase 1:

- Store `date_of_birth` as plain `DATE` and treat it as sensitive data that must not be logged.
- Encrypt only the fields explicitly called out as PII in the spec (names, phone, email).

Rationale:

- Ride eligibility and pricing need age/height on the hot path.
- Encrypting every attribute increases complexity and is harder to justify without a query requirement.

If evaluators treat date of birth as PII, we can revise to store `age_years` + encrypted `date_of_birth` without changing external APIs.

---

## 4. Data Model (Tables)

Naming follows `IMPLEMENTATION.md` database conventions.

### 4.1 Park Reference Tables (Seeded In Phase 1)

#### 4.1.1 `zones`

Purpose: stable zone reference data used across contexts.

Columns:

- `id` (PK)
- `code` (UNIQUE, stable identifier, e.g. `ADVENTURE`)
- `name` (human readable)
- `created_at`, `updated_at`

#### 4.1.2 `park_configurations`

Purpose: park-wide operational configuration.

Columns:

- `id` (PK)
- `active` (BOOLEAN, exactly one true row)
- `max_daily_capacity` (INT, > 0)
- `created_at`, `updated_at`

Notes:

- "exactly one active row" is enforced by application logic plus a uniqueness strategy (documented in implementation).

#### 4.1.3 `seasonal_periods`

Purpose: classify dates into PEAK vs OFF_PEAK.

Columns:

- `id` (PK)
- `start_date` (DATE)
- `end_date` (DATE)
- `season_type` (ENUM-like string: `PEAK`, `OFF_PEAK`)
- `created_at`, `updated_at`

Invariants:

- `start_date <= end_date`
- Periods must not overlap (enforced by validation in service once Park slice exists; Phase 1 uses seed data).

### 4.2 Ticketing Tables

#### 4.2.1 `visitors`

Columns:

- `id` (PK)
- `first_name_enc` (encrypted)
- `last_name_enc` (encrypted)
- `email_enc` (encrypted, nullable)
- `phone_enc` (encrypted, nullable)
- `date_of_birth` (DATE)
- `height_cm` (INT)
- `created_at`, `updated_at`

Notes:

- We keep `date_of_birth` and `height_cm` unencrypted so we can compute age/eligibility quickly. We still treat them as sensitive and never log them.

#### 4.2.2 `wristbands`

Columns:

- `id` (PK)
- `rfid_tag` (UNIQUE, NOT NULL)
- `status` (string: `IN_STOCK`, `ACTIVE`, `DEACTIVATED`)
- `created_at`, `updated_at`

Lifecycle:

- `IN_STOCK` means the RFID is known to the system but not currently assigned.
- `ACTIVE` means it is assigned to an active Visit.
- `DEACTIVATED` means it must never be used again (lost/stolen/retired).

#### 4.2.3 `pass_types`

Columns:

- `id` (PK)
- `code` (UNIQUE: `SINGLE_DAY`, `MULTI_DAY`, `RIDE_SPECIFIC`, `FAMILY`, `FAST_TRACK`)
- `name`
- `description`
- `multi_day_count` (INT, nullable; required when `code = MULTI_DAY`)
- `active` (BOOLEAN)
- `created_at`, `updated_at`

Notes:

- Pass types are configuration data. Phase 1 seeds a starter set.

#### 4.2.4 `pass_type_prices`

Purpose: explicit price matrix.

Columns:

- `id` (PK)
- `pass_type_id` (FK to `pass_types.id`)
- `age_group` (string: `CHILD`, `ADULT`, `SENIOR`)
- `day_type` (string: `WEEKDAY`, `WEEKEND`)
- `season_type` (string: `PEAK`, `OFF_PEAK`)
- `price_cents` (INT, > 0)
- `currency` (string, e.g. `LKR` or `USD`, seeded)
- `created_at`, `updated_at`

Invariants:

- UNIQUE(`pass_type_id`, `age_group`, `day_type`, `season_type`)

#### 4.2.5 `tickets`

Purpose: purchase record with immutable price snapshot.

Columns:

- `id` (PK)
- `visitor_id` (FK to `visitors.id`)
- `pass_type_id` (FK to `pass_types.id`)
- `visit_date` (DATE, the date this ticket is first used)
- `valid_from` (DATE)
- `valid_to` (DATE)
- `price_paid_cents` (INT)
- `currency` (string)
- `purchased_at` (TIMESTAMP)
- `created_at`, `updated_at`

Notes:

- `valid_from/valid_to` are derived from pass type and `visit_date`.
- Price is copied from `pass_type_prices` into the ticket so later price changes do not rewrite history.

#### 4.2.6 `visits`

Purpose: active session for RFID resolution.

Columns:

- `id` (PK)
- `visitor_id` (FK to `visitors.id`)
- `wristband_id` (FK to `wristbands.id`)
- `ticket_id` (FK to `tickets.id`)
- `status` (string: `ACTIVE`, `ENDED`)
- `started_at` (TIMESTAMP)
- `ended_at` (TIMESTAMP, nullable)
- `created_at`, `updated_at`

Invariants:

- A wristband can be attached to at most one ACTIVE visit at a time.
- A visitor can have at most one ACTIVE visit at a time.

We enforce these invariants using service checks plus locking (details in implementation).

#### 4.2.7 `access_entitlements`

Purpose: attach entitlements to a Ticket.

Columns:

- `id` (PK)
- `ticket_id` (FK to `tickets.id`)
- `entitlement_type` (string: `ZONE`, `RIDE`, `QUEUE_PRIORITY`)
- `zone_id` (nullable, LONG)
- `ride_id` (nullable, LONG)
- `priority_level` (nullable, INT)
- `created_at`, `updated_at`

Notes:

- `zone_id` is intended to reference `zones.id`.
- `ride_id` will reference rides once the Rides slice exists.
- Ticketing stores entitlements; Rides enforces them.

#### 4.2.8 `park_day_capacity`

Purpose: concurrency-safe daily capacity enforcement.

Columns:

- `id` (PK)
- `visit_date` (DATE, UNIQUE)
- `max_capacity` (INT)
- `issued_count` (INT)
- `created_at`, `updated_at`

Rules:

- When issuing a ticket for `visit_date`, we increment `issued_count` only if `issued_count < max_capacity`.
- `max_capacity` is copied from the active `park_configurations.max_daily_capacity` at the time the day row is created.

---

## 5. Pricing Algorithm

Inputs:

- Visitor date of birth
- Visit date
- Pass type

Steps:

1. Compute `age_group` from date of birth and visit date.
2. Compute `day_type` from visit date (weekday vs weekend).
3. Compute `season_type` from `seasonal_periods` (default to `OFF_PEAK` if no period matches).
4. Look up the unique `pass_type_prices` row and take its `price_cents` and `currency`.
5. Store the values as a snapshot on `tickets`.

Age group rules (simple, explainable bands):

- CHILD: < 12
- ADULT: 12 to 59
- SENIOR: >= 60

---

## 6. Capacity Enforcement Algorithm

When issuing a ticket for a given `visit_date`:

1. Ensure a `park_day_capacity` row exists for the date. If not, create one using the active `park_configurations.max_daily_capacity`.
2. Perform an atomic increment guarded by `issued_count < max_capacity`.
3. If the increment affects 0 rows, return "Sold Out" as a Business Rule Violation (422).

This ensures that even if multiple ticket counters issue tickets concurrently, we never exceed capacity.

### 6.1 Multi-Day Pass Rule (Designed Now, Implemented Later)

When multi-day passes are enabled, the capacity rule changes from "one day" to "every day the ticket is valid":

- Compute the ticket validity range: `valid_from` to `valid_to`.
- For every date in that range, increment that day's `park_day_capacity.issued_count` using the same guarded update.
- The entire issuance runs inside one database transaction.

If any one day in the range is sold out, the operation fails and the transaction rolls back, meaning:

- no ticket is created
- no visit is started
- no capacity is reserved for any of the days

Phase 1.1 defers multi-day issuance to keep the first implementation vertical and simple.

---

## 7. REST API (Phase 1)

Base path: `/api/ticketing`

All success responses return DTOs directly. Error responses follow the global `ErrorResponse` format in `IMPLEMENTATION.md`.

### 7.1 Pass Types

`GET /api/ticketing/pass-types`

- Purpose: allow staff dashboards to show available pass types.
- Requires (future): `ROLE_TICKET_STAFF` or `ROLE_MANAGER`.

Response fields (per item): `id`, `code`, `name`, `description`, `multiDayCount`, `active`.

### 7.2 Register Visitor

`POST /api/ticketing/visitors`

Purpose: create a Visitor record.

Request fields:

- `firstName` (required)
- `lastName` (required)
- `email` (optional)
- `phone` (optional)
- `dateOfBirth` (required, ISO date)
- `heightCm` (required, integer)

Response fields: `id` plus the stored values (PII returned because this is staff-facing).

PII rule: responses may contain PII; logs must not.

### 7.3 Issue Ticket And Start Visit (Wristband Association)

`POST /api/ticketing/visits`

Purpose: sell a ticket, associate the RFID wristband, and start an ACTIVE visit atomically.

Phase 1.1 scope note: this endpoint initially issues only single-day tickets (including single-day variants like ride-specific and fast-track). Multi-day issuance is added in a later mini-slice.

Request fields:

- `visitorId` (required)
- `rfidTag` (required)
- `passTypeId` (required)
- `visitDate` (optional; defaults to "today" in UTC; must not be in the past)

Visit date rules:

- If `visitDate` is omitted, the server sets it to `LocalDate.now(UTC)`.
- If `visitDate` is provided, it must be `>= LocalDate.now(UTC)`.

Rationale:

- We anchor the default and the validation to UTC to avoid subtle timezone bugs
  where a request is considered "future" or "past" depending on the JVM's
  default timezone. This matches the existing UTC anchoring used in Ticketing
  entity validation.

Wristband handling:

- If `rfidTag` is not known to the system, Ticketing creates a new Wristband row
  in `IN_STOCK` and then activates it as part of the same issuance transaction.
- If the wristband exists but is `ACTIVE` or `DEACTIVATED`, issuance fails with
  a state conflict.

Response fields:

- `visitId`
- `ticketId`
- `wristbandId`
- `pricePaidCents`
- `currency`
- `validFrom`
- `validTo`

Failure cases (examples):

- 404 `VISITOR_NOT_FOUND`
- 404 `PASS_TYPE_NOT_FOUND`
- 409 `WRISTBAND_ALREADY_ACTIVE`
- 409 `WRISTBAND_RFID_TAG_CONFLICT` (two counters attempted to auto-register the same new RFID concurrently; client should retry)
- 409 `VISITOR_ALREADY_IN_PARK`
- 422 `CAPACITY_EXCEEDED` (sold out)
- 422 `PASS_TYPE_INACTIVE`
- 422 `PASS_TYPE_NOT_SUPPORTED_YET` (e.g. `MULTI_DAY` in Phase 1.1)
- 422 `VISIT_DATE_IN_PAST`
- 422 `PRICE_NOT_CONFIGURED` (no seeded price row matches the pricing inputs)

Phase 1.1 enforcement:

- If the selected PassType is `MULTI_DAY`, this endpoint rejects the request as
  a business rule violation. Multi-day issuance is implemented in Phase 1.2.

Entitlements note:

- Phase 1.1 issues the Ticket + Visit and does not yet populate
  `access_entitlements`. Entitlement creation rules are implemented in Phase 1.3.

### 7.4 RFID Resolution Contract (Service-Level)

Other contexts must resolve RFID tags through Ticketing (global rule in `DESIGN.md`).

Phase 1 establishes a service contract:

- Input: `rfidTag`
- Output: active `visitId`, `visitorId`, visitor age/height attributes required for eligibility checks, and ticket/pass entitlements.

We may optionally expose this as a debug endpoint:

`GET /api/ticketing/rfid/{rfidTag}/active-visit`

This endpoint is not intended for production device flows, but is useful for integration testing early.

---

## 8. Cross-Context Interactions

Outgoing calls from Ticketing:

- Park (reference data): read active park configuration, read seasonal periods, list zones

Incoming calls to Ticketing:

- Rides/Food/Merchandise/Events: resolve RFID tag to active Visit

Direction is one-way: scan-processing contexts depend on Ticketing; Ticketing does not depend on them.

---

## 9. Phase 1 Internal Sub-Phasing (To Keep Work Vertical)

We will implement Phase 1 in sub-slices, each still following: detailed design (this doc) -> implementation -> verification.

1. Phase 1.1 Visitor registration, pass type listing, single-day ticket issuance + capacity enforcement, wristband association, active visit creation, RFID resolution.
2. Phase 1.2 Multi-day ticket issuance: reserve capacity for every day in validity window; define how new day visits start for an existing ticket.
3. Phase 1.3 AccessEntitlement creation rules for ride-specific and fast-track passes (data model already present), plus hardening (concurrency edge cases, additional validation, indexes, test coverage expansion).
