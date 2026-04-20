# ATPEZMS Phase 5 (Telemetry) -- Detailed Design

This document is the **Level 2 Detailed Design** for Phase 5 (Telemetry), as required by `DESIGN_RULES.md`.

It refines `DESIGN.md §5.5` (Telemetry bounded context) and the relevant functional requirements into a concrete, implementable design.

Implementation mechanics (Spring class names, annotations, wiring) are in `PHASE_05_TELEMETRY_IMPLEMENTATION.md`.

---

## 1. Scope

### 1.1 Primary Goal

Phase 5 delivers the Telemetry bounded context: a flat, append-only log that records every wristband scan event in the system. Each scan event captures when, where, why, and with what outcome a wristband was scanned. This data feeds two downstream consumers:

- **Security (SE-3):** Duplicate/unauthorized RFID detection operates on this log.
- **Analytics (FR-MG2):** Congestion heat map is derived from scan event density per zone over time.

This is **Phase 5.1** (a single sub-slice). There is no Phase 5.2 currently planned.

### 1.2 Functional Requirements Covered

| Requirement | Description |
|---|---|
| SE-3 | Duplicate / Unauthorized RFID Detection -- scan event log provides the raw data for detecting simultaneous use of the same RFID tag at distant locations |
| FR-MG2 | Congestion Heat Map -- visitor density per zone derived from scan event timestamps and zone IDs |
| DESIGN.md §3.10 | Scan Event Logging -- every wristband scan generates a scan event in the Telemetry context |

### 1.3 Explicit Non-Goals

- **Cross-context validation:** Telemetry does not validate RFID tags, zones, or visit status. It records what it is told. Validation is the caller's responsibility.
- **Real-time alerting:** Telemetry stores data; it does not trigger alerts. Alert logic belongs to Security (Phase 10 Safety) or Analytics (Phase 12).
- **Data retention / archiving:** No TTL, no archival policy. All scan events are retained indefinitely. This is acceptable for a course project with H2.
- **Streaming / event bus:** Telemetry uses synchronous database writes. No Kafka, no message queue.
- **DELETE / PUT / PATCH:** ScanEvent is an append-only audit log. Once written, it is never modified or deleted.
- **PII encryption:** ScanEvent contains no PII. The RFID tag is a device identifier, not visitor PII. No `@Convert(converter = StringEncryptionConverter.class)` is needed.
- **Idempotency:** Telemetry is a logging endpoint. Duplicate scan events are valid (the same wristband can be scanned twice in quick succession). Idempotency is the caller's concern, not Telemetry's.

### 1.4 Security Note

Phase 3.2 (Security Skeleton) is not yet implemented. All Telemetry endpoints are currently unauthenticated (same as existing Ticketing, Park, and Billing endpoints). When security is eventually added, Telemetry write endpoints will require the role of the calling context (e.g., `ROLE_RIDE_OPERATOR` for ride scans, `ROLE_FOOD_STAFF` for POS scans). The GET endpoint will require `ROLE_MANAGER` or `ROLE_ADMIN`. This is documented but not enforced in code yet.

---

## 2. Key Decisions (With Rationale)

### 2.1 ScanEvent: Flat Log, No JPA Relationships

ScanEvent stores all references as scalar values (`Long`, `String`). There are no `@ManyToOne` or `@OneToMany` relationships to entities in other contexts.

**Why no relationships?** Telemetry is a log, not a domain model. The scan event records a point-in-time fact: "RFID tag X was scanned at device Y in zone Z for purpose P, and the decision was D." If the referenced zone is later renamed or deleted, the historical scan event must still be queryable. A JPA relationship would create a FK constraint that could prevent zone deletion or cause orphan records. Storing scalar IDs avoids this.

This is consistent with the cross-context rule from DESIGN.md §6.2: "store the referenced entity's ID as a plain value."

### 2.2 Append-Only: No Mutations

Once a ScanEvent is persisted, it is never modified. This is enforced at three levels:

1. **JPA:** The ScanEvent entity has no mutator methods (setters). The `update()` method does not exist.
2. **API:** No PUT/PATCH/DELETE endpoints for scan events. Only POST (create) and GET (read).
3. **Business rule:** A scan event is a historical record of what happened at a specific moment. Modifying it would silently alter the audit trail, making security analysis unreliable.

**Why not soft-delete?** There is no legitimate reason to "delete" a scan event. If a scan was recorded in error (e.g., a test scan), it remains in the log as evidence that the scan attempt occurred. This is the same reasoning as Billing's append-only transaction ledger (Phase 4, §2.1).

### 2.3 No Cross-Context Validation

When a caller records a scan event, Telemetry does not:
- Verify that the RFID tag corresponds to an active visit
- Verify that the zone ID exists
- Verify that the device identifier is registered

**Why?** Telemetry is a logging sink, not a gatekeeper. The caller (Rides, Food, Merchandise, Events, Ticketing) has already performed its own validation before deciding to allow or deny the scan. Telemetry's job is to record the outcome, not to re-validate it. Adding validation here would:
1. Create circular dependencies (Telemetry would need to call back into Ticketing, Park, etc.)
2. Add latency to the scan path (violating PR-1 < 1 second)
3. Create a single point of failure (if Telemetry's validation fails, the primary operation fails too)

### 2.4 Purpose Enum: Explicit Scan Contexts

The `ScanPurpose` enum captures why the scan occurred. The values are:

```java
public enum ScanPurpose {
    RIDE_ENTRY,    // Visitor scanned to enter a ride queue
    RIDE_EXIT,     // Visitor scanned to exit a ride queue (left without boarding)
    POS_SALE,      // Visitor scanned at a POS terminal for a food/merchandise purchase
    KIOSK_RESERVATION,  // Visitor scanned at a kiosk to make an event reservation
    GATE_ENTRY,    // Visitor scanned to enter the park (visit start)
    GATE_EXIT      // Visitor scanned to exit the park (visit end, checkout)
}
```

**Why separate RIDE_ENTRY and RIDE_EXIT?** Queue analytics need to distinguish between entering and leaving a queue. A visitor who enters a queue but leaves without boarding is a different data point than one who boards.

**Why POS_SALE covers both food and merchandise?** The purpose is the *type of interaction*, not the *category of goods*. Both food and merchandise use a POS terminal to charge the wristband. The distinction between food and merchandise is captured in the Billing transaction's `TransactionSource`, not in the scan event.

**Why GATE_ENTRY and GATE_EXIT?** These are the park boundary scans. They are the primary data points for FR-MG2 (congestion heat map) because they indicate when a visitor enters or leaves the park.

### 2.5 Decision Enum: Allowed or Denied

```java
public enum ScanDecision {
    ALLOWED,   // The scan was accepted; the visitor was granted access
    DENIED     // The scan was rejected; the visitor was denied access
}
```

**Why not a third state like "PENDING"?** The scan decision is final at the time of the scan. The turnstile either opens or it doesn't. There is no intermediate state.

### 2.6 Reason Field: Nullable, Populated Only on Denial

The `reason` field is a nullable `String`. It is populated only when `decision` is `DENIED`. When `decision` is `ALLOWED`, `reason` is `null`.

**Why not always populate a reason?** An allowed scan has no meaningful reason -- the visitor was simply granted access. Populating a reason like "Valid wristband" for every allowed scan would waste storage and add noise to analytics queries.

**Why not a separate enum for denial reasons?** Denial reasons are free-form text because they come from different contexts with different validation logic:
- Rides: "Height requirement not met", "Ride at capacity"
- Ticketing: "Wristband not active", "Visit not started"
- Billing: "Outstanding balance"
A fixed enum would need to be updated every time a new denial reason is added across contexts. A free-form string is more flexible.

### 2.7 deviceIdentifier: String Field

The `deviceIdentifier` is a `String` that identifies the hardware device that performed the scan. This could be:
- A turnstile ID (e.g., "turnstile-ride-01", "gate-main-exit-03")
- A POS terminal ID (e.g., "pos-foodcourt-02")
- A kiosk ID (e.g., "kiosk-events-01")

**Why not a typed enum or FK?** The set of devices is open-ended and managed outside the application. A new turnstile or POS terminal can be added without a code change. The device identifier is an opaque string that the caller provides.

### 2.8 zoneId: Long Scalar

The `zoneId` is a `Long` that references a Zone from the Park context by ID. It is not a JPA relationship.

**Why not validate zoneId exists?** Same reasoning as §2.3. Telemetry records what it is told. If a caller passes an invalid zoneId, that is the caller's bug, not Telemetry's responsibility to catch.

### 2.9 rfidTag: String

The `rfidTag` is the raw RFID tag value that was scanned. It is a `String` (not a FK to Wristband).

**Why not a FK to Wristband?** The same reasoning as §2.1. The RFID tag is a scalar value that may or may not correspond to a known wristband. SE-3 (duplicate/unauthorized RFID detection) specifically needs to find scans of RFID tags that are not associated with any active wristband. A FK constraint would prevent recording such scans.

### 2.10 Indexing Strategy

Two indexes support the primary query patterns:

1. **`idx_scans_rfid_tag`** on `(rfid_tag)`: Supports SE-3 queries that find all scans for a given RFID tag (to detect duplicate simultaneous use).
2. **`idx_scans_zone_timestamp`** on `(zone_id, timestamp)`: Supports FR-MG2 queries that count scans per zone within a time window (congestion heat map).

**Why not index on `device_identifier`?** No current consumer queries by device. If needed later, an index can be added via migration.

**Why not index on `purpose` or `decision`?** These are low-cardinality enums. A full table scan with a WHERE clause on a 2-6 value enum is efficient enough for the expected data volume. If analytics queries prove otherwise, a composite index can be added later.

### 2.11 Fire-and-Forget Semantics

Per DESIGN.md §3.10: "The context processing a wristband scan is responsible for calling the Telemetry service to log the scan event. This is a fire-and-forget call that must not be on the PR-1 critical path."

**Implementation approach:** The caller wraps the Telemetry call in a try/catch. If the telemetry log fails, the primary operation still succeeds. The telemetry write is synchronous within the caller's transaction boundary for simplicity in Phase 5. Future phases may migrate to an application event with `@TransactionalEventListener(phase = AFTER_COMMIT)` for true fire-and-forget semantics.

**Why synchronous now?** Phase 5 is the first slice to use Telemetry. Adding an event bus or async infrastructure now would be premature. The synchronous call is simple, testable, and correct. The fire-and-forget wrapper (try/catch) ensures that telemetry failures don't break the primary operation.

---

## 3. Entity

### 3.1 ScanEvent

Package: `com.atpezms.atpezms.telemetry.entity`

| Field | Type | Column | Constraints |
|---|---|---|---|
| id | Long | BIGINT AUTO_INCREMENT | PK |
| timestamp | Instant | TIMESTAMP NOT NULL | When the scan occurred |
| rfidTag | String | VARCHAR(100) NOT NULL | Raw RFID tag value scanned |
| deviceIdentifier | String | VARCHAR(100) NOT NULL | Hardware device that performed the scan |
| zoneId | Long | BIGINT NOT NULL | Zone where the scan occurred (FK to Park's Zone by ID) |
| purpose | ScanPurpose | VARCHAR(30) NOT NULL | Why the scan occurred |
| decision | ScanDecision | VARCHAR(10) NOT NULL | Outcome of the scan |
| reason | String | VARCHAR(500) (nullable) | Denial reason; null when decision is ALLOWED |

Extends BaseEntity (inherits `createdAt`, `updatedAt`). Note: `timestamp` is the business timestamp (when the scan actually happened, as reported by the caller), while `createdAt` is the persistence timestamp (when the row was written). These may differ slightly due to network latency.

### 3.2 Enums

| Enum | Values | Stored As |
|---|---|---|
| ScanPurpose | RIDE_ENTRY, RIDE_EXIT, POS_SALE, KIOSK_RESERVATION, GATE_ENTRY, GATE_EXIT | VARCHAR(30) via `@Enumerated(EnumType.STRING)` |
| ScanDecision | ALLOWED, DENIED | VARCHAR(10) via `@Enumerated(EnumType.STRING)` |

---

## 4. Endpoints

### 4.1 POST /api/telemetry/scans

Record a new scan event.

**Request:**
```json
{
  "rfidTag": "RFID-ABC-12345",
  "deviceIdentifier": "turnstile-ride-01",
  "zoneId": 1,
  "purpose": "RIDE_ENTRY",
  "decision": "ALLOWED",
  "reason": null
}
```

**Validation:**
- rfidTag: not blank, max 100 chars
- deviceIdentifier: not blank, max 100 chars
- zoneId: not null, positive
- purpose: not null (must be a valid ScanPurpose value)
- decision: not null (must be a valid ScanDecision value)
- reason: nullable, max 500 chars (if provided)

**Business rules:**
- No validation of rfidTag, zoneId, or deviceIdentifier against other contexts
- If decision is DENIED and reason is blank, the service sets reason to "No reason provided" (defensive default)
- Timestamp is set by the service using the injected Clock (not provided by the caller)

**Response:** 201 Created with ScanEventResponse.

### 4.2 GET /api/telemetry/scans

Query scan events with optional filtering.

**Query parameters (all optional):**
- `rfidTag` (String): Filter by exact RFID tag match
- `zoneId` (Long): Filter by zone ID
- `purpose` (String): Filter by scan purpose (enum name, e.g., "RIDE_ENTRY")
- `decision` (String): Filter by scan decision ("ALLOWED" or "DENIED")
- `from` (ISO 8601 Instant): Filter scans from this timestamp (inclusive)
- `to` (ISO 8601 Instant): Filter scans up to this timestamp (inclusive)
- `page` (int, default 0): Page number
- `size` (int, default 20, max 100): Page size

**Response:** 200 OK with `Page<ScanEventResponse>`.

**Rationale for pagination:** ScanEvent is an append-only log that grows indefinitely. Without pagination, a GET request could return millions of rows. Spring Data's `Pageable` provides offset/limit semantics with total count.

---

## 5. DTOs

### RecordScanRequest

```java
public record RecordScanRequest(
    @NotBlank @Size(max = 100) String rfidTag,
    @NotBlank @Size(max = 100) String deviceIdentifier,
    @NotNull @Positive Long zoneId,
    @NotNull ScanPurpose purpose,
    @NotNull ScanDecision decision,
    @Size(max = 500) String reason
) {}
```

`reason` is nullable. The service layer applies a defensive default when decision is DENIED and reason is blank.

### ScanEventResponse

```java
public record ScanEventResponse(
    Long id,
    Instant timestamp,
    String rfidTag,
    String deviceIdentifier,
    Long zoneId,
    String purpose,
    String decision,
    String reason,
    Instant createdAt
) {
    // Static factory from(ScanEvent)
}
```

---

## 6. Exceptions

**No custom exceptions needed.** Telemetry is an append-only logging context with no business rules that can fail. The only failure modes are:

- **Validation failure (400):** Caught by Spring's `MethodArgumentNotValidException` handler.
- **Unexpected internal error (500):** Caught by the global exception handler's catch-all.

There is no "ScanEvent not found" because there is no endpoint that reads a single scan event by ID. The GET endpoint returns a paginated list, which is never empty in a way that constitutes an error.

---

## 7. Test Strategy

### 7.1 Controller Tests (@WebMvcTest)

**TelemetryControllerTest:**

| Test | Expected |
|---|---|
| Record scan → 201 | Valid request creates scan event |
| Record scan → 400 | Missing rfidTag |
| Record scan → 400 | Missing deviceIdentifier |
| Record scan → 400 | Null zoneId |
| Record scan → 400 | Null purpose |
| Record scan → 400 | Null decision |
| Record scan → 400 | Reason exceeds 500 chars |
| GET scans → 200 | Returns paginated results |
| GET scans with rfidTag filter → 200 | Returns only matching scans |
| GET scans with zoneId filter → 200 | Returns only matching scans |
| GET scans with date range → 200 | Returns scans within range |

### 7.2 Integration Tests (@SpringBootTest)

**TelemetryIntegrationTest:**

| Test | Expected |
|---|---|
| Record scan → persisted | Scan event saved with correct fields |
| Record denied scan with reason | Reason field populated |
| Record denied scan without reason | Defensive default reason applied |
| Record multiple scans | All scans persisted independently |
| GET scans → paginated | Pagination works with default page/size |
| GET scans filtered by rfidTag | Only matching scans returned |
| GET scans filtered by zone + time window | Only matching scans returned |
| GET scans filtered by purpose | Only matching scans returned |
| GET scans filtered by decision | Only matching scans returned |
| Timestamp uses injected Clock | Timestamp matches expected test time |

---

## 8. Cross-Context Dependencies

### 8.1 Inbound (Callers → Telemetry)

| Caller | Purpose | Method |
|---|---|---|
| Rides | Log ride entry/exit scans | `TelemetryService.recordScan()` |
| Food | Log POS scan at food outlet | `TelemetryService.recordScan()` |
| Merchandise | Log POS scan at store | `TelemetryService.recordScan()` |
| Events | Log kiosk reservation scan | `TelemetryService.recordScan()` |
| Ticketing | Log gate entry/exit scans | `TelemetryService.recordScan()` |

All callers invoke Telemetry's service method directly. Telemetry does not call back into any other context.

### 8.2 Outbound (Telemetry → Consumers)

Telemetry has **no outbound calls**. It is a pure write-and-read context. Consumers (Security, Analytics) read from Telemetry's tables directly:
- Security queries `scan_events` by `rfid_tag` to detect duplicate simultaneous scans.
- Analytics queries `scan_events` by `zone_id` and `timestamp` to build congestion heat maps.

These are read-only queries from other contexts, consistent with the Analytics pattern from DESIGN.md §5.12.

### 8.3 Dependency Direction

```
Rides ──→ Telemetry ←── (read-only) ── Security
Food ───→ Telemetry ←── (read-only) ── Analytics
Merch ──→ Telemetry
Events ──→ Telemetry
Ticketing → Telemetry
```

No circular dependencies. Telemetry is a leaf node that only receives writes.

---

## 9. Step Plan

### Phase 5.1 Steps

1. Write V009 migration (scan_events table with indexes).
2. Add enums: ScanPurpose, ScanDecision.
3. Add ScanEvent entity.
4. Add ScanEventRepository.
5. Add DTOs: RecordScanRequest, ScanEventResponse.
6. Add TelemetryService (recordScan, findAll with filtering).
7. Add TelemetryController (POST /scans, GET /scans).
8. Write TelemetryControllerTest.
9. Write TelemetryIntegrationTest.
10. Run `./gradlew test`. Fix any issues.
11. Adversarial review.
12. Commit.
