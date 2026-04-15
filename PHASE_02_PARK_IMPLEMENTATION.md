# ATPEZMS Phase 2 (Park) -- Implementation Notes

This document is the **Level 2 implementation companion** for Phase 2 (Park). It specifies the concrete Spring/JPA mechanics that bring `PHASE_02_PARK_DESIGN.md` to life.

Cross-cutting rules (naming, DTO conventions, transaction placement, etc.) are in `IMPLEMENTATION.md`. This document only records Phase-2-specific decisions that are not already covered there.

---

## 1. New Exceptions

All new exceptions extend an existing category class from `IMPLEMENTATION.md §4.1`.

| Class | Extends | Error Code | HTTP |
|---|---|---|---|
| `ZoneNotFoundException` | `ResourceNotFoundException` | `ZONE_NOT_FOUND` | 404 |
| `ZoneCodeAlreadyExistsException` | `DuplicateResourceException` | `ZONE_CODE_ALREADY_EXISTS` | 409 |
| `NoActiveParkConfigurationException` | `ResourceNotFoundException` | `NO_ACTIVE_PARK_CONFIGURATION` | 404 |
| `CapacityReductionConflictException` | `BusinessRuleViolationException` | `CAPACITY_REDUCTION_CONFLICT` | 422 |
| `SeasonalPeriodNotFoundException` | `ResourceNotFoundException` | `SEASONAL_PERIOD_NOT_FOUND` | 404 |
| `SeasonalPeriodInvalidDatesException` | `BusinessRuleViolationException` | `SEASONAL_PERIOD_INVALID_DATES` | 422 |
| `SeasonalPeriodDateConflictException` | `BusinessRuleViolationException` | `SEASONAL_PERIOD_DATE_CONFLICT` | 422 |

All exception classes live in `com.atpezms.atpezms.park.exception` (new package, created as needed).

---

## 2. Flyway Migrations

### V005 -- Add description and active to zones
File: `V005__add_zone_description_and_active.sql`

```sql
-- H2 requires one column per ALTER TABLE statement.
ALTER TABLE zones ADD COLUMN description VARCHAR(500);
ALTER TABLE zones ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
```

### V006 -- Add park_write_lock anchor table
File: `V006__add_park_write_lock.sql`

```sql
CREATE TABLE park_write_lock (
    id         INT       NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_park_write_lock PRIMARY KEY (id)
);

INSERT INTO park_write_lock (id, created_at) VALUES (1, CURRENT_TIMESTAMP);
```

---

## 3. Entities

### 3.1 Zone (Updated)

Add two fields to the existing `Zone` entity:

```java
@Column(name = "description", length = 500)
private String description;      // nullable

@Column(name = "active", nullable = false)
private boolean active = true;
```

Getters for both. No setter -- mutation goes through explicit service-layer methods (or direct field set inside the entity method `update(name, description, active)`).

Add a package-private update method on the entity (keeps mutation logic inside the entity class):

```java
void update(String name, String description, boolean active) { ... }
```

`code` remains read-only (no setter).

### 3.2 ParkWriteLock (New Entity)

A minimal entity mapped to the `park_write_lock` table. Used only for pessimistic locking.

```java
@Entity
@Table(name = "park_write_lock")
public class ParkWriteLock extends BaseEntity {
    // no business fields; the row exists solely to be locked
}
```

> **Education note:** `BaseEntity` adds `createdAt`/`updatedAt`. The lock table only has `id` and `created_at`; do NOT extend `BaseEntity` here -- instead map `id` and `created_at` manually (or strip `updatedAt` from the table definition if H2 validate would fail). **Implementation decision:** Keep the lock table simple -- no `BaseEntity`, just `id` and `created_at`. Map it as a standalone entity with `@Id` only.

Revised entity:

```java
@Entity
@Table(name = "park_write_lock")
public class ParkWriteLock {
    @Id
    private int id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ParkWriteLock() {}
}
```

---

## 4. Repositories

### 4.1 ZoneRepository (Updated)

Add:
```java
boolean existsByCode(String code);
List<Zone> findAllByOrderByCodeAsc();
List<Zone> findAllByActiveTrueOrderByCodeAsc();
Optional<Zone> findByCode(String code);  // useful for human-readable admin lookups
```

### 4.2 ParkWriteLockRepository (New)

```java
public interface ParkWriteLockRepository extends JpaRepository<ParkWriteLock, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM ParkWriteLock l WHERE l.id = 1")
    Optional<ParkWriteLock> acquireLock();
}
```

### 4.3 ParkConfigurationRepository (Updated)

Add:
```java
List<ParkConfiguration> findAllByOrderByIdDesc();

@Modifying
@Query("UPDATE ParkConfiguration c SET c.active = false WHERE c.active = true")
int deactivateAll();
```

The `deactivateAll` method bypasses entity lifecycle (IMPLEMENTATION.md §6.1) -- it does not call `@PreUpdate`. Since `ParkConfiguration` has no `updatedAt` field set by `@LastModifiedDate` via entity manager (it does via `BaseEntity` listener), the JPQL update must explicitly set `updatedAt`:

```java
@Modifying
@Query("UPDATE ParkConfiguration c SET c.active = false, c.updatedAt = :now WHERE c.active = true")
int deactivateAll(@Param("now") Instant now);
```

### 4.4 ParkDayCapacityRepository (Updated)

Add two methods to support capacity propagation:

```java
// Find all future (today+) capacity rows with issued_count > a given max
@Query("SELECT p FROM ParkDayCapacity p WHERE p.visitDate >= :today AND p.issuedCount > :newMax")
List<ParkDayCapacity> findConflictingFutureRows(@Param("today") LocalDate today, @Param("newMax") int newMax);

// Bulk update max_capacity for today and future rows
@Modifying
@Query("UPDATE ParkDayCapacity p SET p.maxCapacity = :newMax, p.updatedAt = :now WHERE p.visitDate >= :today")
int updateMaxCapacityFromToday(@Param("newMax") int newMax, @Param("today") LocalDate today, @Param("now") Instant now);
```

### 4.5 SeasonalPeriodRepository (Updated)

Add overlap detection query:

```java
// Finds any period whose [startDate, endDate] overlaps [from, to], optionally excluding a given ID
@Query("""
    SELECT s FROM SeasonalPeriod s
    WHERE s.startDate <= :to AND s.endDate >= :from
    AND (:excludeId IS NULL OR s.id <> :excludeId)
    """)
List<SeasonalPeriod> findOverlapping(
    @Param("from") LocalDate from,
    @Param("to") LocalDate to,
    @Param("excludeId") Long excludeId);
```

---

## 5. Services

### 5.1 ZoneService (New)

Package: `com.atpezms.atpezms.park.service`

Methods:
- `listZones(boolean activeOnly)` -- `@Transactional(readOnly = true)`
- `getZone(Long id)` -- `@Transactional(readOnly = true)`
- `createZone(CreateZoneRequest)` -- `@Transactional`
- `updateZone(Long id, UpdateZoneRequest)` -- `@Transactional`

`createZone` validates the `code` format against `[A-Z0-9_]+` (regex check in service) before checking uniqueness. If format fails, throw a `MethodArgumentNotValidException`-equivalent by rejecting the field -- but since this is a cross-field service check, throw a custom `BusinessRuleViolationException` with code `ZONE_CODE_INVALID_FORMAT` (422), or add a custom `@Pattern` constraint on the DTO field and let Bean Validation do it at 400. **Implementation decision:** put the regex as a Bean Validation `@Pattern` on the `CreateZoneRequest.code` field so it results in a 400 at the boundary (most correct; format is a syntactic constraint, not a domain rule).

### 5.2 ParkConfigurationService (New)

Package: `com.atpezms.atpezms.park.service`

Methods:
- `listConfigurations()` -- `@Transactional(readOnly = true)`
- `getActiveConfiguration()` -- `@Transactional(readOnly = true)`
- `createAndActivateConfiguration(CreateParkConfigurationRequest)` -- `@Transactional`

`createAndActivateConfiguration` steps (all in one `@Transactional` method):
1. `parkWriteLockRepository.acquireLock()` -- acquires the pessimistic write lock.
2. `configRepository.deactivateAll(Instant.now(clock))` -- deactivates any current active row.
3. Check `park_day_capacity` for conflicts (§4.4 `findConflictingFutureRows`). If any exist, throw `CapacityReductionConflictException` with the earliest conflicting date in the message.
4. Persist new `ParkConfiguration(true, newMaxDailyCapacity)`.
5. If `issuedCount` check passed, bulk-update `park_day_capacity` (`updateMaxCapacityFromToday`).
6. Return response DTO.

### 5.3 SeasonalPeriodService (New)

Package: `com.atpezms.atpezms.park.service`

Methods:
- `listPeriods()` -- `@Transactional(readOnly = true)`
- `getPeriod(Long id)` -- `@Transactional(readOnly = true)`
- `createPeriod(CreateSeasonalPeriodRequest)` -- `@Transactional`
- `deletePeriod(Long id)` -- `@Transactional`

`createPeriod` steps:
1. Validate `endDate >= startDate` (if not, throw `SeasonalPeriodInvalidDatesException`).
2. `parkWriteLockRepository.acquireLock()` -- serializes the write.
3. `seasonRepository.findOverlapping(startDate, endDate, null)` -- check for overlaps. If any found, throw `SeasonalPeriodDateConflictException`.
4. Persist new `SeasonalPeriod(startDate, endDate, seasonType)`.
5. Return response DTO.

---

## 6. DTOs

All DTOs are Java records. Request DTOs carry Bean Validation. Response DTOs do not.

### Zone DTOs

**`CreateZoneRequest`:**
```java
record CreateZoneRequest(
    @NotBlank @Size(min=1, max=50) @Pattern(regexp = "[A-Z0-9_]+") String code,
    @NotBlank @Size(min=1, max=100) String name,
    @Size(max=500) String description,   // nullable
    Boolean active                        // null defaults to true in service
) {}
```

**`UpdateZoneRequest`:**
```java
record UpdateZoneRequest(
    @NotBlank @Size(min=1, max=100) String name,
    @Size(max=500) String description,   // null means clear description
    @NotNull Boolean active
) {}
```

**`ZoneResponse`:** `id`, `code`, `name`, `description`, `active`, `createdAt`, `updatedAt`.

### ParkConfiguration DTOs

**`CreateParkConfigurationRequest`:**
```java
record CreateParkConfigurationRequest(
    @NotNull @Min(1) Integer maxDailyCapacity
) {}
```

**`ParkConfigurationResponse`:** `id`, `active`, `maxDailyCapacity`, `createdAt`, `updatedAt`.

### SeasonalPeriod DTOs

**`CreateSeasonalPeriodRequest`:**
```java
record CreateSeasonalPeriodRequest(
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @NotNull SeasonType seasonType
) {}
```

**`SeasonalPeriodResponse`:** `id`, `startDate`, `endDate`, `seasonType`, `createdAt`, `updatedAt`.

---

## 7. Controllers

All controllers are in `com.atpezms.atpezms.park.controller`.

- `ZoneController` -- `@RequestMapping("/api/park/zones")`
- `ParkConfigurationController` -- `@RequestMapping("/api/park/configurations")`
- `SeasonalPeriodController` -- `@RequestMapping("/api/park/seasonal-periods")`

All controllers annotated with `@Validated` (for potential path-variable validation, even if not currently used -- consistent with IMPLEMENTATION.md §8.1).

`GET` collection methods return `ResponseEntity<List<...>>` with 200.
`GET` single-resource methods return `ResponseEntity<...>` with 200.
`POST` methods return `ResponseEntity<...>` with 201.
`PUT` methods return `ResponseEntity<...>` with 200.
`DELETE` methods return `ResponseEntity<Void>` with 204.

---

## 8. Testing Strategy

For each sub-slice, produce:
- **Controller tests (`@WebMvcTest`):** test validation, HTTP status codes, and error mapping. Mock the service layer.
- **Integration tests (`@SpringBootTest` + `@ActiveProfiles("test")`):** test full persistence behavior, including concurrency-safety scenarios (sequential transaction commits, not actual threads -- IMPLEMENTATION.md §10).

Key integration test scenarios per sub-slice:

**2.1 Zone CRUD:**
- Create zone → 201, readable by GET.
- Create with duplicate code → 409.
- Create with invalid code format → 400.
- Update zone (name/description/active) → 200, changes persisted.
- GET with `activeOnly=true` returns only active zones.

**2.2 ParkConfiguration:**
- Create new config → 201, old config deactivated.
- `GET /active` returns new config.
- `GET /configurations` shows full history.
- Capacity reduction with no conflicting future rows → 201.
- Capacity reduction with conflicting `park_day_capacity` row → 422.
- Capacity increase → always succeeds.

**2.3 SeasonalPeriod:**
- Create non-overlapping period → 201.
- Create overlapping period → 422.
- Delete period → 204, gone from list.
- `endDate < startDate` → 422.

---

## 9. Step Plan

### Phase 2.1 -- Zone CRUD
1. V005 + V006 migrations.
2. Update `Zone` entity (add `description`, `active`, `update()` method).
3. Add `ParkWriteLock` entity + `ParkWriteLockRepository`.
4. Update `ZoneRepository`.
5. Add `park.exception` package with `ZoneNotFoundException` and `ZoneCodeAlreadyExistsException`.
6. Implement `ZoneService`.
7. Add Zone DTOs.
8. Implement `ZoneController`.
9. Tests.

### Phase 2.2 -- ParkConfiguration management
1. Update `ParkConfigurationRepository` (deactivateAll, etc).
2. Update `ParkDayCapacityRepository` (conflict check, bulk update).
3. Add `NoActiveParkConfigurationException`, `CapacityReductionConflictException`.
4. Implement `ParkConfigurationService`.
5. Add ParkConfiguration DTOs.
6. Implement `ParkConfigurationController`.
7. Tests.

### Phase 2.3 -- SeasonalPeriod management
1. Update `SeasonalPeriodRepository` (findOverlapping).
2. Add `SeasonalPeriodNotFoundException`, `SeasonalPeriodInvalidDatesException`, `SeasonalPeriodDateConflictException`.
3. Implement `SeasonalPeriodService`.
4. Add SeasonalPeriod DTOs.
5. Implement `SeasonalPeriodController`.
6. Tests.
