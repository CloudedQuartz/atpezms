# ATPEZMS Phase 5 (Telemetry) -- Implementation Notes

This document is the **Level 2 implementation companion** for Phase 5 (Telemetry). It specifies the concrete Spring/JPA mechanics that bring `PHASE_05_TELEMETRY_DESIGN.md` to life.

Cross-cutting rules (naming, DTO conventions, transaction placement, etc.) are in `IMPLEMENTATION.md`. This document records only Phase-5-specific decisions not already covered there.

---

## 1. New Exceptions

**No new exceptions required.** Telemetry is an append-only logging context. All error handling is covered by the existing global exception handler:

| Scenario | Exception | HTTP |
|---|---|---|
| Validation failure (missing/invalid fields) | `MethodArgumentNotValidException` (Spring) | 400 |
| Unexpected internal error | `Exception` (catch-all) | 500 |

No `ScanEventNotFoundException` or similar is needed because there is no endpoint that reads a single scan event by ID.

---

## 2. Gradle Dependency Changes

**No new dependencies required.** Phase 5 uses only JPA, Spring MVC, and validation -- all already present from prior phases.

---

## 3. Flyway Migration

### V009 -- Telemetry schema (scan_events table)

File: `V009__add_telemetry_scan_events.sql`

```sql
-- Phase 5 (Telemetry): Append-only scan event log.
--
-- scan_events: Every wristband scan in the system is recorded here.
-- This is a flat log with no JPA relationships to other contexts.
-- Immutability is enforced at the entity level (no setters) and at
-- the API level (no PUT/PATCH/DELETE endpoints).
--
-- Consumers:
--   - Security (SE-3): duplicate/unauthorized RFID detection via rfid_tag lookups
--   - Analytics (FR-MG2): congestion heat map via zone_id + timestamp range queries

CREATE TABLE scan_events (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    timestamp           TIMESTAMP    NOT NULL,
    rfid_tag            VARCHAR(100) NOT NULL,
    device_identifier   VARCHAR(100) NOT NULL,
    zone_id             BIGINT       NOT NULL,
    purpose             VARCHAR(30)  NOT NULL,
    decision            VARCHAR(10)  NOT NULL,
    reason              VARCHAR(500),
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    CONSTRAINT pk_scan_events PRIMARY KEY (id),
    CONSTRAINT chk_scans_purpose CHECK (purpose IN (
        'RIDE_ENTRY', 'RIDE_EXIT', 'POS_SALE',
        'KIOSK_RESERVATION', 'GATE_ENTRY', 'GATE_EXIT'
    )),
    CONSTRAINT chk_scans_decision CHECK (decision IN ('ALLOWED', 'DENIED'))
);

-- Index for SE-3: find all scans for a given RFID tag (duplicate detection)
CREATE INDEX idx_scans_rfid_tag ON scan_events(rfid_tag);

-- Index for FR-MG2: count scans per zone within a time window (congestion heat map)
CREATE INDEX idx_scans_zone_timestamp ON scan_events(zone_id, timestamp);
```

**Why CHECK constraints on enums?** Defense in depth, matching the pattern from Phase 4 (V008 billing tables). The JPA `@Enumerated(EnumType.STRING)` prevents invalid enum values at the application layer, but a direct SQL INSERT could bypass it.

**Why no `created_by`/`updated_by` columns?** Telemetry scan events are machine-generated (from hardware devices), not staff-initiated. There is no authenticated actor to attribute the event to at this stage (Phase 3.2 Security is not yet implemented). When security is added, the device's service account could be captured, but that is a future enhancement.

**Why `timestamp` separate from `created_at`?** The `timestamp` field captures when the scan *actually happened* (as reported by the caller's hardware clock). The `created_at` field captures when the row was *persisted* to the database (managed by JPA auditing). These may differ by milliseconds due to network latency. Analytics queries should use `timestamp` (the business event time), not `created_at` (the persistence time).

---

## 4. Entities

### 4.1 Enums

Package: `com.atpezms.atpezms.telemetry.entity`

```java
public enum ScanPurpose {
    RIDE_ENTRY,
    RIDE_EXIT,
    POS_SALE,
    KIOSK_RESERVATION,
    GATE_ENTRY,
    GATE_EXIT
}
```

```java
public enum ScanDecision {
    ALLOWED,
    DENIED
}
```

Enum values are uppercase strings. Stored via `@Enumerated(EnumType.STRING)` in the database.

### 4.2 ScanEvent Entity

Package: `com.atpezms.atpezms.telemetry.entity`

```java
package com.atpezms.atpezms.telemetry.entity;

import com.atpezms.atpezms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "scan_events")
public class ScanEvent extends BaseEntity {

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "rfid_tag", nullable = false, length = 100)
    private String rfidTag;

    @Column(name = "device_identifier", nullable = false, length = 100)
    private String deviceIdentifier;

    @Column(name = "zone_id", nullable = false)
    private Long zoneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private ScanPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 10)
    private ScanDecision decision;

    @Column(name = "reason", length = 500)
    private String reason;

    protected ScanEvent() {}

    public ScanEvent(Instant timestamp, String rfidTag, String deviceIdentifier,
                     Long zoneId, ScanPurpose purpose, ScanDecision decision,
                     String reason) {
        if (timestamp == null) throw new IllegalArgumentException("timestamp must not be null");
        if (rfidTag == null || rfidTag.isBlank()) throw new IllegalArgumentException("rfidTag must not be blank");
        if (deviceIdentifier == null || deviceIdentifier.isBlank()) throw new IllegalArgumentException("deviceIdentifier must not be blank");
        if (zoneId == null || zoneId <= 0) throw new IllegalArgumentException("zoneId must be positive");
        if (purpose == null) throw new IllegalArgumentException("purpose must not be null");
        if (decision == null) throw new IllegalArgumentException("decision must not be null");

        this.timestamp = timestamp;
        this.rfidTag = rfidTag;
        this.deviceIdentifier = deviceIdentifier;
        this.zoneId = zoneId;
        this.purpose = purpose;
        this.decision = decision;
        this.reason = reason;
    }

    // Getters only -- no setters (append-only log)
    public Instant getTimestamp() { return timestamp; }
    public String getRfidTag() { return rfidTag; }
    public String getDeviceIdentifier() { return deviceIdentifier; }
    public Long getZoneId() { return zoneId; }
    public ScanPurpose getPurpose() { return purpose; }
    public ScanDecision getDecision() { return decision; }
    public String getReason() { return reason; }
}
```

**Why no setters?** The ScanEvent entity is append-only. Once created, it is never modified. Removing setters enforces this at the code level, matching the pattern from Billing's Transaction entity.

**Why constructor validation?** Guards against null/blank values at the entity level, providing a second line of defense beyond DTO-level Bean Validation. If a caller bypasses the API and constructs a ScanEvent directly (e.g., in a test), the constructor will reject invalid data.

**Why `timestamp` instead of relying on `createdAt`?** The `timestamp` field represents when the scan *actually occurred* (the business event time), set by the service using the injected Clock. The `createdAt` field represents when the row was *persisted* (managed by JPA auditing). These differ when there is network latency between the scan and the database write. Analytics queries must use `timestamp` for accurate time-window calculations.

---

## 5. Repository

### ScanEventRepository

Package: `com.atpezms.atpezms.telemetry.repository`

```java
package com.atpezms.atpezms.telemetry.repository;

import com.atpezms.atpezms.telemetry.entity.ScanEvent;
import com.atpezms.atpezms.telemetry.entity.ScanDecision;
import com.atpezms.atpezms.telemetry.entity.ScanPurpose;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanEventRepository extends JpaRepository<ScanEvent, Long> {

    /**
     * Find all scans for a given RFID tag, ordered by timestamp descending.
     * Used by SE-3 (duplicate/unauthorized RFID detection).
     */
    List<ScanEvent> findByRfidTagOrderByTimestampDesc(String rfidTag);

    /**
     * Find scans in a specific zone within a time window.
     * Used by FR-MG2 (congestion heat map).
     */
    List<ScanEvent> findByZoneIdAndTimestampBetweenOrderByTimestampAsc(
            Long zoneId, Instant from, Instant to);

    /**
     * Paginated query with optional filters for the GET /api/telemetry/scans endpoint.
     * All filter parameters are nullable -- when null, that filter is not applied.
     */
    Page<ScanEvent> findByRfidTagAndZoneIdAndPurposeAndDecisionAndTimestampBetween(
            String rfidTag,
            Long zoneId,
            ScanPurpose purpose,
            ScanDecision decision,
            Instant from,
            Instant to,
            Pageable pageable);
}
```

**Why `findByRfidTagOrderByTimestampDesc` returns `List` not `Page`?** SE-3 queries typically look at recent scans for a single RFID tag. The result set is small (a visitor doesn't generate thousands of scans). Pagination is unnecessary.

**Why `findByZoneIdAndTimestampBetweenOrderByTimestampAsc` returns `List`?** FR-MG2 analytics queries read all scans in a zone within a time window to compute density. The caller (Analytics context) will aggregate the results. Pagination would break the aggregation logic. If the data volume becomes too large, Analytics can narrow the time window.

**Why the big `findBy...And...And...` method for pagination?** Spring Data JPA treats `null` parameters in derived query methods as "don't filter on this field." This means a single method handles all filter combinations without needing `@Query` with dynamic JPQL. For example:
- `findByRfidTagAndZoneIdAndPurposeAndDecisionAndTimestampBetween("ABC", null, null, null, null, null, pageable)` → filters only by rfidTag
- `findByRfidTagAndZoneIdAndPurposeAndDecisionAndTimestampBetween(null, 1L, null, null, from, to, pageable)` → filters by zoneId and timestamp range

**Why not use `@Query` with `@Param` and optional parameters?** The derived query method approach is simpler and avoids JPQL string concatenation. Spring Data generates the correct SQL based on which parameters are non-null.

---

## 6. Services

### TelemetryService

Package: `com.atpezms.atpezms.telemetry.service`

```java
package com.atpezms.atpezms.telemetry.service;

import com.atpezms.atpezms.telemetry.dto.RecordScanRequest;
import com.atpezms.atpezms.telemetry.dto.ScanEventResponse;
import com.atpezms.atpezms.telemetry.entity.ScanDecision;
import com.atpezms.atpezms.telemetry.entity.ScanEvent;
import com.atpezms.atpezms.telemetry.entity.ScanPurpose;
import com.atpezms.atpezms.telemetry.repository.ScanEventRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core telemetry logic: record scan events and query them.
 *
 * This is an append-only logging service. There are no business rules
 * that can fail -- the service records what it is told.
 */
@Service
@Transactional(readOnly = true)
public class TelemetryService {

    private final ScanEventRepository scanEventRepository;
    private final Clock clock;

    public TelemetryService(ScanEventRepository scanEventRepository, Clock clock) {
        this.scanEventRepository = scanEventRepository;
        this.clock = clock;
    }

    /**
     * Record a new scan event.
     *
     * Timestamp is set by the service using the injected Clock (not provided by
     * the caller). This ensures consistent time handling and testability.
     *
     * If decision is DENIED and reason is blank, a defensive default is applied.
     */
    @Transactional
    public ScanEventResponse recordScan(RecordScanRequest request) {
        Instant timestamp = Instant.now(clock);

        String reason = request.reason();
        if (request.decision() == ScanDecision.DENIED
                && (reason == null || reason.isBlank())) {
            reason = "No reason provided";
        }

        ScanEvent event = new ScanEvent(
                timestamp,
                request.rfidTag(),
                request.deviceIdentifier(),
                request.zoneId(),
                request.purpose(),
                request.decision(),
                reason
        );

        scanEventRepository.save(event);
        return ScanEventResponse.from(event);
    }

    /**
     * Query scan events with optional filters.
     *
     * All filter parameters are nullable. When null, that filter is not applied.
     * Results are paginated to handle the append-only nature of the log.
     */
    public Page<ScanEventResponse> findAll(
            String rfidTag,
            Long zoneId,
            ScanPurpose purpose,
            ScanDecision decision,
            Instant from,
            Instant to,
            Pageable pageable) {

        Page<ScanEvent> events = scanEventRepository
                .findByRfidTagAndZoneIdAndPurposeAndDecisionAndTimestampBetween(
                        rfidTag, zoneId, purpose, decision, from, to, pageable);

        return events.map(ScanEventResponse::from);
    }
}
```

**Why `@Transactional(readOnly = true)` at class level?** All methods default to read-only. The `recordScan` method overrides with `@Transactional` (read-write). This is the same pattern used in BillingService.

**Why does the service set the timestamp, not the caller?** Centralizing time handling in the service via the injected Clock ensures:
1. Consistent time source across all scan events (no clock skew between callers)
2. Testability (tests can inject a fixed Clock)
3. The caller doesn't need to know about time handling -- it just reports what happened

**Why the defensive default for denial reason?** A denied scan without a reason is a data quality issue. Setting a default ensures that the `reason` field is never null for denied scans, making analytics queries simpler (no need to handle null reasons separately).

---

## 7. DTOs

### RecordScanRequest

Package: `com.atpezms.atpezms.telemetry.dto`

```java
package com.atpezms.atpezms.telemetry.dto;

import com.atpezms.atpezms.telemetry.entity.ScanDecision;
import com.atpezms.atpezms.telemetry.entity.ScanPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RecordScanRequest(
    @NotBlank @Size(max = 100) String rfidTag,
    @NotBlank @Size(max = 100) String deviceIdentifier,
    @NotNull @Positive Long zoneId,
    @NotNull ScanPurpose purpose,
    @NotNull ScanDecision decision,
    @Size(max = 500) String reason
) {}
```

`reason` is nullable (no `@NotNull`). The service layer applies a defensive default when decision is DENIED and reason is blank.

### ScanEventResponse

Package: `com.atpezms.atpezms.telemetry.dto`

```java
package com.atpezms.atpezms.telemetry.dto;

import com.atpezms.atpezms.telemetry.entity.ScanEvent;
import java.time.Instant;

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
    public static ScanEventResponse from(ScanEvent event) {
        return new ScanEventResponse(
            event.getId(),
            event.getTimestamp(),
            event.getRfidTag(),
            event.getDeviceIdentifier(),
            event.getZoneId(),
            event.getPurpose().name(),
            event.getDecision().name(),
            event.getReason(),
            event.getCreatedAt()
        );
    }
}
```

Enums are serialized as strings (`.name()`) in the response. This matches the pattern from Billing's TransactionResponse and BillResponse.

---

## 8. Controller

### TelemetryController

Package: `com.atpezms.atpezms.telemetry.controller`

Base path: `/api/telemetry`

```java
package com.atpezms.atpezms.telemetry.controller;

import com.atpezms.atpezms.telemetry.dto.RecordScanRequest;
import com.atpezms.atpezms.telemetry.dto.ScanEventResponse;
import com.atpezms.atpezms.telemetry.entity.ScanDecision;
import com.atpezms.atpezms.telemetry.entity.ScanPurpose;
import com.atpezms.atpezms.telemetry.service.TelemetryService;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin controller for telemetry operations.
 *
 * All business logic is delegated to TelemetryService.
 *
 * Note: @PreAuthorize annotations will be added when Phase 3.2 (Security) is implemented.
 * POST endpoint will require the role of the calling context (e.g., ROLE_RIDE_OPERATOR).
 * GET endpoint will require ROLE_MANAGER or ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/telemetry")
@Validated
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    /**
     * Record a new scan event.
     *
     * Requires: ROLE_RIDE_OPERATOR / ROLE_FOOD_STAFF / ROLE_STORE_STAFF
     *           / ROLE_EVENT_COORDINATOR / ROLE_TICKET_STAFF (depending on caller)
     */
    @PostMapping("/scans")
    public ResponseEntity<ScanEventResponse> recordScan(
            @RequestBody @Valid RecordScanRequest request) {
        ScanEventResponse response = telemetryService.recordScan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Query scan events with optional filters.
     *
     * Requires: ROLE_MANAGER or ROLE_ADMIN
     */
    @GetMapping("/scans")
    public ResponseEntity<Page<ScanEventResponse>> getScans(
            @RequestParam(required = false) String rfidTag,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) ScanPurpose purpose,
            @RequestParam(required = false) ScanDecision decision,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ScanEventResponse> response = telemetryService.findAll(
                rfidTag, zoneId, purpose, decision, from, to, pageable);
        return ResponseEntity.ok(response);
    }
}
```

**Why `@PageableDefault(size = 20)`?** Default page size of 20 is reasonable for a log viewer. The maximum of 100 is enforced by Spring Data's `@PageableDefault` -- callers can request larger pages up to the Spring Data default max (which is unbounded unless configured). If a hard max is needed, a `Pageable` validator can be added later.

**Why `@RequestParam(required = false)` for all filters?** This allows callers to omit any filter they don't need. Spring Data's derived query methods treat null parameters as "don't filter."

---

## 9. Test Strategy

### 9.1 Controller Tests (@WebMvcTest)

**TelemetryControllerTest:**

| Test | Expected |
|---|---|
| Record scan → 201 | Valid request creates scan event, returns 201 with response body |
| Record scan → 400 (missing rfidTag) | Blank rfidTag triggers validation error |
| Record scan → 400 (missing deviceIdentifier) | Blank deviceIdentifier triggers validation error |
| Record scan → 400 (null zoneId) | Null zoneId triggers validation error |
| Record scan → 400 (null purpose) | Null purpose triggers validation error |
| Record scan → 400 (null decision) | Null decision triggers validation error |
| Record scan → 400 (reason too long) | Reason > 500 chars triggers validation error |
| GET scans → 200 (no filters) | Returns paginated empty page |
| GET scans → 200 (with rfidTag filter) | Passes rfidTag to service |
| GET scans → 200 (with zoneId filter) | Passes zoneId to service |
| GET scans → 200 (with date range) | Passes from/to to service |

### 9.2 Integration Tests (@SpringBootTest)

**TelemetryIntegrationTest:**

| Test | Expected |
|---|---|
| Record allowed scan → persisted | Scan event saved with all fields, decision=ALLOWED, reason=null |
| Record denied scan with reason | reason field populated with provided value |
| Record denied scan without reason | Defensive default "No reason provided" applied |
| Record multiple scans | All scans persisted independently, each with unique ID |
| GET scans → paginated | Returns Page with correct content and metadata |
| GET scans filtered by rfidTag | Only scans matching rfidTag returned |
| GET scans filtered by zoneId | Only scans matching zoneId returned |
| GET scans filtered by purpose | Only scans matching purpose returned |
| GET scans filtered by decision | Only scans matching decision returned |
| GET scans filtered by date range | Only scans within [from, to] returned |
| GET scans with combined filters | All filters applied correctly |
| Timestamp uses injected Clock | Timestamp matches expected test time |

---

## 10. Step Plan

1. Write V009 migration (scan_events table with indexes).
2. Add enums: ScanPurpose, ScanDecision.
3. Add ScanEvent entity.
4. Add ScanEventRepository.
5. Add DTOs: RecordScanRequest, ScanEventResponse.
6. Add TelemetryService.
7. Add TelemetryController.
8. Write TelemetryControllerTest.
9. Write TelemetryIntegrationTest.
10. Run `./gradlew test`. Fix any issues.
11. Adversarial review.
12. Commit.
