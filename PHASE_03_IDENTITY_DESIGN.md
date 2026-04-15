# ATPEZMS Phase 3 (Identity + Security Skeleton) -- Detailed Design

This document is the **Level 2 Detailed Design** for Phase 3, as required by `DESIGN_RULES.md`.

It refines `DESIGN.md §4` (Security Architecture) and `DESIGN.md §5.2` (Identity bounded context) into a concrete, implementable design.

Implementation mechanics (Spring class names, annotations, wiring) are in `PHASE_03_IDENTITY_IMPLEMENTATION.md`.

---

## 1. Scope

### 1.1 Primary Goal

Phase 3 delivers two things in two sub-slices:

1. **Phase 3.1 -- Identity:** The `StaffUser` and `Role` domain model, and the staff user management API (`/api/identity/users`). Staff users can be created, listed, updated (roles changed), and deactivated by a system administrator.

2. **Phase 3.2 -- Security Skeleton:** Spring Security's JWT filter chain, the login endpoint (`POST /api/identity/login`), role-based authorization enforcement on every existing endpoint, actor auditing (`createdBy`/`updatedBy`) wired into `BaseEntity`, and updates to all existing tests to pass the security filter.

### 1.2 Functional and Security Requirements Covered

- **SE-2** (administrative functions restricted to Manager/Admin role) -- Phase 3.2 enforces the role restrictions that every prior phase only annotated in comments.
- **FR-VT3, FR-RQ1, etc. (operational endpoints)** -- all existing endpoints become role-protected.
- `DESIGN.md §4.1` (JWT stateless authentication) and `§4.2` (RBAC).

### 1.3 Explicit Non-Goals For Phase 3

- Refresh tokens: not in the spec, unnecessary complexity for a park shift-length token.
- Password reset / self-service credential management: out of scope.
- Visitor authentication: visitors never log in; they interact via RFID wristband (`DESIGN.md §4.3`).
- Rate limiting / brute-force protection: not in spec; deferred.
- RS256 (asymmetric JWT signing): HS256 is used for this monolith (see §2.3).
- Adding `createdBy`/`updatedBy` columns to `park_write_lock` (it does not extend `BaseEntity`).

---

## 2. Key Decisions (With Rationale)

### 2.1 Entity Naming: `StaffUser` (Not `User`)

`USER` is a reserved keyword in SQL (and in H2 specifically). Mapping a JPA entity named `User` to a table named `users` causes query failures. The safe solution is to name the entity `StaffUser` mapped to the table `staff_users`.

Why "staff" rather than "app" or "system"? The spec uses the term "staff" (ATPEZMS.md §2) and this context only manages staff accounts and device service accounts -- not the visitor, who is a data entity in Ticketing. The name is self-documenting.

### 2.2 Role Stored As `@ElementCollection` (Not A JPA Entity)

The eight system roles (`ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_TICKET_STAFF`, etc.) are a **fixed, closed set defined by the specification**. There is no use case for listing all possible roles, creating new roles at runtime, or querying which users hold a given role as a standalone operation.

A `@ManyToMany` JPA relationship to a `roles` table would add a `Role` entity, a `RoleRepository`, a join table, and a service method for querying, all to represent a fixed enum. This is YAGNI.

**Decision:** `Role` is a Java enum. Each `StaffUser` holds a `Set<Role>` persisted via `@ElementCollection` into a `staff_user_roles` join table. Spring Security reads the set and maps it to `GrantedAuthority` instances.

Why a `Set` and not a single role per user? A user can hold multiple roles (e.g., a senior operator who is both `ROLE_RIDE_OPERATOR` and `ROLE_MANAGER`). A `Set` mirrors reality and maps naturally to Spring Security's multi-authority model.

### 2.3 JWT Algorithm: HS256 (HMAC-SHA256) With A Symmetric Secret Key

**HS256 vs RS256:**

| Concern | HS256 (symmetric) | RS256 (asymmetric) |
|---|---|---|
| Key management | One secret key (both sign and verify) | Private key signs; public key verifies |
| Suitable for | Single-server monolith | Distributed systems where multiple resource servers need to verify tokens |
| Complexity | Low | Higher (key pair generation, rotation) |

ATPEZMS is a monolith. Every JWT is both issued and verified by the same server process. There is no scenario where a second server needs the public key without the private key. RS256 would add complexity -- private/public key pair management, PEM encoding, key rotation -- for no architectural benefit.

**Decision:** HS256 with a 256-bit (32-byte) secret provided via `atpezms.jwt.secret` (Base64-encoded in `application.properties`, consistent with the PII encryption key pattern in `IMPLEMENTATION.md §13.4`).

### 2.4 JWT Library: Spring Security's `oauth2-jose` (No Third-Party JWT Library)

Spring Boot 4 / Spring Security 7 includes full JWT support via the `spring-boot-starter-oauth2-resource-server` starter:

- `JwtEncoder` (`NimbusJwtEncoder`) -- signs and serializes JWT claims into a compact token string.
- `JwtDecoder` (`NimbusJwtDecoder`) -- parses, verifies signature, and decodes claims from a token string.

These are first-party Spring APIs. They integrate directly with the `BearerTokenAuthenticationFilter` (part of the resource-server starter) which automatically extracts the `Authorization: Bearer <token>` header, validates the token, and populates the `SecurityContextHolder`.

Using a third-party library (e.g., `io.jsonwebtoken:jjwt`) would require manually writing the filter, parsing the header, and calling `SecurityContextHolder.setAuthentication(...)`. The Spring-native approach does all of that automatically.

### 2.5 JWT Claims

The JWT payload carries:

| Claim | Value | Purpose |
|---|---|---|
| `sub` | Username (unique, lowercase) | Identifies the authenticated principal |
| `roles` | `["ROLE_ADMIN"]` (JSON array) | Drives `@PreAuthorize` checks |
| `iat` | Issue timestamp (epoch seconds) | Standard claim |
| `exp` | Expiry timestamp (epoch seconds) | Standard claim |

**JWT expiry: 8 hours.** A typical park staff shift is 8 hours. The token expires at the end of the shift, so staff must re-authenticate each day. No refresh token mechanism is implemented (out of spec scope).

### 2.6 Login Endpoint: `POST /api/identity/login`

Per `IMPLEMENTATION.md §2.3`, all endpoint base paths follow the `/api/<context>` convention. The Identity context owns credentials and authentication, so the login endpoint lives at `/api/identity/login`.

Why not `/api/auth/login`? That would require a new `auth` context package that holds one endpoint. The Identity context already manages the user and credential data needed for authentication; putting the login there avoids creating a one-class context just for routing.

On authentication failure (wrong username, wrong password, or deactivated account), the response is always `401 INVALID_CREDENTIALS` with the same message, regardless of which check failed. This prevents **username enumeration**: an attacker cannot distinguish "this username doesn't exist" from "this username exists but the password is wrong."

**Timing side-channel defence:** Spring Security's `DaoAuthenticationProvider` hides the not-found/wrong-password distinction at the message level, but a subtler timing attack exists: when the username does not exist, no BCrypt verification runs and the response returns in ~0ms hash time; when the username exists but the password is wrong, BCrypt runs for ~250ms. An attacker can distinguish valid usernames from invalid ones by measuring response latency. To close this, `StaffUserDetailsService.loadUserByUsername` must run a dummy BCrypt verification (against a pre-computed constant hash) when the user is not found (see `PHASE_03_IDENTITY_IMPLEMENTATION.md §6.3`).

### 2.7 Custom Security Error Responses: `AuthenticationEntryPoint` and `AccessDeniedHandler`

Spring Security processes JWT validation **in the filter chain**, before a request reaches any controller. When authentication fails, Spring Security calls `AuthenticationEntryPoint.commence(...)` directly -- this bypasses `GlobalExceptionHandler` entirely.

Similarly, when a valid JWT lacks the required role, Spring calls `AccessDeniedHandler.handle(...)` before the controller is invoked.

To keep error responses consistent with `ErrorResponse` (our standard error envelope from `IMPLEMENTATION.md §4`), we configure:

- **`CustomAuthenticationEntryPoint`**: returns `401` with code `UNAUTHORIZED`, message `"Authentication required"`.
- **`CustomAccessDeniedHandler`**: returns `403` with code `FORBIDDEN`, message `"Insufficient permissions"`.

This keeps the 401/403 response format identical to other errors, as required by `DESIGN.md §3.1` (consistent error structure).

### 2.8 CSRF and Session Management

**CSRF is disabled.** CSRF (Cross-Site Request Forgery) attacks require that the browser automatically attaches credentials (cookies, session IDs) to cross-origin requests. JWT-based APIs use `Authorization: Bearer <token>` headers, which browsers do not automatically send cross-origin. There is nothing for CSRF to protect against.

**Session management is STATELESS.** Spring Security's default creates an HTTP session to store the `SecurityContext`. We disable this: `sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))`. The security context is reconstructed from the JWT on every request. No session is ever created or stored. This is required for the distributed hardware model described in `DESIGN.md §4.1`.

### 2.9 Actor Auditing: `createdBy` / `updatedBy` Added In Phase 3.2

`IMPLEMENTATION.md §3` deferred actor auditing to the Security slice. In Phase 3.2 we:

1. Add `@CreatedBy` and `@LastModifiedBy` fields to `BaseEntity`, backed by `AuditorAware<String>`.
2. `AuditorAwareImpl` reads the current username from `SecurityContextHolder`. Returns `Optional.empty()` if no authenticated user (pre-auth requests fail at the filter layer before reaching any service; this case is reached only by genuinely public endpoints like `/api/identity/login`).
3. A single Flyway migration (V008) adds `created_by VARCHAR(100)` and `updated_by VARCHAR(100)` as **nullable** columns to all existing entity tables. Existing rows will have `NULL` for both, which is correct -- we do not know who created them.

Tables that need the migration: `zones`, `park_configurations`, `seasonal_periods`, `park_day_capacity`, `visitors`, `wristbands`, `tickets`, `pass_types`, `pass_type_prices`, `visits`, `access_entitlements`. (Excludes `park_write_lock`, which does not extend `BaseEntity`.)

### 2.10 Password Hashing: BCrypt

Passwords are **never stored in plaintext**. They are hashed with BCrypt via Spring Security's `BCryptPasswordEncoder` before persistence. BCrypt is a slow, adaptive hash function specifically designed for passwords -- it is computationally expensive to brute-force even if the database is compromised.

The entity stores only `passwordHash: String`. There is no `password` column. The service never reads the hash back for comparison -- Spring Security's `DaoAuthenticationProvider` handles that via `UserDetailsService`.

### 2.11 Test Security Strategy

After Phase 3.2 enables security, every existing endpoint that requires a role will return 401 for unauthenticated requests. All existing integration tests (which call endpoints without authentication) will break.

To fix this without making tests brittle, we use Spring Security Test support:

- **`@WebMvcTest` controller tests:** annotate the test method (or class) with `@WithMockUser(roles = {"ADMIN"})` (or whatever role the endpoint requires). This injects a mock `Authentication` into the security context without needing a real JWT. The controller test stays focused on controller logic; it does not test the JWT filter.
- **`@SpringBootTest` integration tests:** use `MockMvc` with `.with(SecurityMockMvcRequestPostProcessors.jwt().authorities(...))` on each request. This simulates a validated JWT without actually signing/verifying one.

Why not disable security for the `test` profile? That would leave security untested. The goal is: controller tests verify HTTP mapping and service delegation; integration tests verify end-to-end behavior including that the correct role gates are applied.

---

## 3. Domain Model

### 3.1 StaffUser

| Field | Type | Notes |
|---|---|---|
| `id` | `Long` (from BaseEntity) | Auto-generated PK |
| `username` | `VARCHAR(100)`, NOT NULL, UNIQUE | Login identifier. Lowercase by convention. Immutable after creation. |
| `passwordHash` | `VARCHAR(255)`, NOT NULL | BCrypt hash. Never the plaintext password. |
| `fullName` | `VARCHAR(200)`, NOT NULL | Display name for audit records and dashboards. |
| `active` | `BOOLEAN`, NOT NULL, DEFAULT TRUE | `false` = deactivated account. Cannot log in. NOT soft-deleted; the row is retained. |
| `roles` | `Set<Role>` | Persisted via `@ElementCollection` in `staff_user_roles`. At least one role required. |
| `createdAt`, `updatedAt` | `Instant` (from BaseEntity) | JPA auditing timestamps. |
| `createdBy`, `updatedBy` | `String` (from BaseEntity) | Actor auditing. Added in Phase 3.2. |

Why `active` instead of soft-delete? The spec does not require deactivated accounts to be permanently erased. `active = false` blocks login (checked by `UserDetailsService`) while retaining the account for audit trail purposes. The account record may appear in audit logs (`createdBy`, `updatedBy` on other entities). Deleting it would create dangling references.

### 3.2 Role Enum

All eight roles from `DESIGN.md §4.2`, as a Java enum:

```
ROLE_ADMIN            -- System administrator; all endpoints
ROLE_MANAGER          -- Park manager; read + configuration endpoints
ROLE_TICKET_STAFF     -- Ticket counter; visitor registration, ticket issuance
ROLE_RIDE_OPERATOR    -- Ride staff; ride operations, queue monitoring
ROLE_FOOD_STAFF       -- Food court staff; food orders, wristband scans
ROLE_STORE_STAFF      -- Merchandise staff; store sales, wristband scans
ROLE_EVENT_COORDINATOR -- Event management; show scheduling, reservations
ROLE_KIOSK            -- Device service account; kiosk reservation endpoints
```

The `ROLE_` prefix is Spring Security's convention. `GrantedAuthority` instances use the full string (including prefix). `@PreAuthorize("hasRole('ADMIN')")` strips the prefix automatically; `hasAuthority('ROLE_ADMIN')` requires the full string.

---

## 4. REST API

Base path: `/api/identity`

All user management endpoints require `ROLE_ADMIN`. Before Phase 3.2 enforces security, they carry the standard `// Requires: ROLE_ADMIN` comment.

### 4.1 `GET /api/identity/users`

- Purpose: list all staff users, ordered by username ascending.
- Requires: `ROLE_ADMIN`
- Response: 200 OK, list of `StaffUserResponse` (does NOT include `passwordHash`).

### 4.2 `GET /api/identity/users/{id}`

- Purpose: get a single staff user by ID.
- Requires: `ROLE_ADMIN`
- Failure: 404 `STAFF_USER_NOT_FOUND`
- Response: 200 OK.

### 4.3 `POST /api/identity/users`

- Purpose: create a new staff user.
- Requires: `ROLE_ADMIN`
- Request fields:
  - `username` (required; 3–100 chars; pattern `[a-z][a-z0-9._-]*`; immutable after creation)
  - `password` (required; min 8 chars; service will hash it)
  - `fullName` (required; 1–200 chars)
  - `roles` (required; non-empty set of valid Role enum values)
- Failure cases:
  - 409 `USERNAME_ALREADY_EXISTS` -- username is taken.
  - 400 `VALIDATION_FAILED` -- missing/invalid fields.
- Response: 201 Created with `StaffUserResponse`.

Why enforce a `[a-z][a-z0-9._-]*` pattern for usernames? Usernames appear in audit records (`createdBy`, `updatedBy`). The letter-start requirement prevents ambiguous values like `"..."` or `"---"` that could be confused with a truncated or missing audit field. Allowing spaces, Unicode, or shell-special characters would complicate audit record display and cross-system correlation.

### 4.4 `PUT /api/identity/users/{id}/roles`

- Purpose: replace a user's full role set.
- Requires: `ROLE_ADMIN`
- Request fields:
  - `roles` (required; non-empty set of valid Role enum values)
- Failure: 404 `STAFF_USER_NOT_FOUND`
- Response: 200 OK with updated `StaffUserResponse`.

Why a sub-resource (`/roles`) rather than `PUT /api/identity/users/{id}`? A full-user PUT would require re-sending the password (or ignoring it). The role set is the only mutable field after creation (aside from deactivation). A dedicated sub-resource is more expressive and avoids the password-in-update problem.

HTTP method note: `PUT` is used here, not `PATCH`. `IMPLEMENTATION.md §2.3` defines `PUT` as "full update" and `PATCH` as "partial update". `PUT /api/identity/users/{id}/roles` replaces the **entire** role collection in one operation -- this is a full replacement of the `roles` sub-resource, which is `PUT` semantics. Contrast this with `/api/ticketing/wristbands/{id}/scan` (a verb sub-resource representing an imperative action, not a resource state replacement): `/roles` is a noun resource being replaced, not an action verb. The HTTP method is correct; the distinction from verb sub-resources is: noun sub-resources use `GET/PUT/PATCH`; verb sub-resources use `POST`.

### 4.5 `DELETE /api/identity/users/{id}`

- Purpose: deactivate (not hard-delete) a staff user.
- Requires: `ROLE_ADMIN`
- Failure: 404 `STAFF_USER_NOT_FOUND`
- Success: 204 No Content.

The endpoint is `DELETE` by HTTP convention for "remove access", but the underlying operation sets `active = false`. The user record is retained for audit history. A deactivated user cannot log in (checked in `UserDetailsService`).

**Guard:** an admin cannot deactivate their own account. This prevents a scenario where the last admin locks themselves out. If the request's authenticated username matches `user.username`, the operation is rejected with 422 `CANNOT_DEACTIVATE_SELF`.

### 4.6 `POST /api/identity/login`

- Purpose: authenticate with username + password, receive a JWT.
- Requires: **public** (no authentication required).
- Request fields:
  - `username` (required, string)
  - `password` (required, string)
- Failure cases:
  - 401 `INVALID_CREDENTIALS` -- username does not exist, password is wrong, or account is deactivated. **Same response in all three cases** to prevent username enumeration.
  - 400 `VALIDATION_FAILED` -- missing required fields.
- Success: 200 OK with `LoginResponse` (token, expiresAt, username, roles).

Note on the success status code: `200 OK` is used, not `201 Created`. No resource is being created; a token is being issued in response to a credential check. `200` is the correct semantic.

---

## 5. JWT Contract

### 5.1 Token Structure

Standard three-part compact serialization: `header.payload.signature`.

**Header:**
```json
{ "alg": "HS256", "typ": "JWT" }
```

**Payload (claims):**
```json
{
  "sub": "jsmith",
  "roles": ["ROLE_TICKET_STAFF"],
  "iat": 1744902000,
  "exp": 1744930800
}
```

**Signature:** HMAC-SHA256 of `base64url(header).base64url(payload)` using the configured secret key.

### 5.2 Validation Rules

When a request arrives with a `Bearer` token, Spring's `BearerTokenAuthenticationFilter` + `JwtDecoder` validates:

1. **Signature** -- HMAC-SHA256 with the configured key. Failure → 401.
2. **Expiry (`exp`)** -- token must not be expired. Failure → 401.
3. **`sub` presence** -- subject must be non-empty. Failure → 401.

If validation passes, the filter constructs a `JwtAuthenticationToken` from the `roles` claim and places it in `SecurityContextHolder`. Downstream controllers see a populated `Authentication` object.

### 5.3 Key Configuration

Configured via `atpezms.jwt.secret` (Base64-encoded 32-byte random value). Follows the same pattern as the PII encryption key (`atpezms.encryption.key`, see `IMPLEMENTATION.md §13.4`):

- Test and dev: committed placeholder key is acceptable (test database is in-memory, no real credentials).
- Production: inject via environment variable or secrets manager.

---

## 6. Security Authorization Map

The full endpoint authorization table, to be enforced via `SecurityConfig` and `@PreAuthorize` in Phase 3.2:

### Park Context

| Endpoint | Method | Required Role(s) |
|---|---|---|
| `/api/park/zones` | GET | MANAGER, ADMIN |
| `/api/park/zones/{id}` | GET | MANAGER, ADMIN |
| `/api/park/zones` | POST | ADMIN, MANAGER |
| `/api/park/zones/{id}` | PUT | ADMIN, MANAGER |
| `/api/park/configurations` | GET | MANAGER, ADMIN |
| `/api/park/configurations/active` | GET | TICKET_STAFF, MANAGER, ADMIN |
| `/api/park/configurations` | POST | ADMIN, MANAGER |
| `/api/park/seasonal-periods` | GET | MANAGER, ADMIN |
| `/api/park/seasonal-periods/{id}` | GET | MANAGER, ADMIN |
| `/api/park/seasonal-periods` | POST | ADMIN, MANAGER |
| `/api/park/seasonal-periods/{id}` | DELETE | ADMIN, MANAGER |

### Ticketing Context

| Endpoint | Method | Required Role(s) |
|---|---|---|
| `/api/ticketing/visitors` | POST | TICKET_STAFF, MANAGER, ADMIN |
| `/api/ticketing/pass-types` | GET | TICKET_STAFF, MANAGER, ADMIN |
| `/api/ticketing/visits` | POST | TICKET_STAFF, MANAGER, ADMIN |
| `/api/ticketing/rfid/{rfidTag}/active-visit` | GET | TICKET_STAFF, RIDE_OPERATOR, FOOD_STAFF, STORE_STAFF, EVENT_COORDINATOR, MANAGER, ADMIN |

### Identity Context

| Endpoint | Method | Required Role(s) |
|---|---|---|
| `/api/identity/users` | GET | ADMIN |
| `/api/identity/users/{id}` | GET | ADMIN |
| `/api/identity/users` | POST | ADMIN |
| `/api/identity/users/{id}/roles` | PUT | ADMIN |
| `/api/identity/users/{id}` | DELETE | ADMIN |
| `/api/identity/login` | POST | **public** |

---

## 7. Data Model Changes

### V007: Staff Users Schema

```sql
CREATE TABLE staff_users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT pk_staff_users PRIMARY KEY (id),
    CONSTRAINT uk_staff_users_username UNIQUE (username)
);

CREATE TABLE staff_user_roles (
    staff_user_id BIGINT      NOT NULL,
    role          VARCHAR(50) NOT NULL,
    CONSTRAINT pk_staff_user_roles PRIMARY KEY (staff_user_id, role),
    CONSTRAINT fk_staff_user_roles_user
        FOREIGN KEY (staff_user_id) REFERENCES staff_users(id)
);
```

The `staff_user_roles` table uses a composite PK `(staff_user_id, role)` to enforce uniqueness (a user cannot have the same role twice) at the database level, independent of the application.

**Why a composite PK and not a surrogate PK?** There is no entity that needs to reference an individual row in `staff_user_roles`. The composite PK is sufficient and makes the constraint visible in the schema without an extra column.

**Seed data:** V007 also seeds a default admin account:
```sql
INSERT INTO staff_users (id, username, password_hash, full_name, active, created_at, updated_at)
VALUES (1, 'admin', '<bcrypt hash of "changeme">', 'System Administrator', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO staff_user_roles (staff_user_id, role) VALUES (1, 'ROLE_ADMIN');
```

The default password `changeme` is a placeholder. Operators must change it immediately. The design doc records this as an operational requirement.

Why seed an admin? Without an initial admin account, there is no way to create the first user via the API (which requires `ROLE_ADMIN`). The chicken-and-egg problem requires a bootstrapped account.

### V008: Actor Audit Columns

```sql
-- H2 requires one column per ALTER TABLE statement.
ALTER TABLE staff_users         ADD COLUMN created_by VARCHAR(100);
ALTER TABLE staff_users         ADD COLUMN updated_by VARCHAR(100);
ALTER TABLE zones               ADD COLUMN created_by VARCHAR(100);
ALTER TABLE zones               ADD COLUMN updated_by VARCHAR(100);
ALTER TABLE park_configurations ADD COLUMN created_by VARCHAR(100);
ALTER TABLE park_configurations ADD COLUMN updated_by VARCHAR(100);
ALTER TABLE seasonal_periods    ADD COLUMN created_by VARCHAR(100);
ALTER TABLE seasonal_periods    ADD COLUMN updated_by VARCHAR(100);
ALTER TABLE park_day_capacity   ADD COLUMN created_by VARCHAR(100);
ALTER TABLE park_day_capacity   ADD COLUMN updated_by VARCHAR(100);
ALTER TABLE visitors            ADD COLUMN created_by VARCHAR(100);
ALTER TABLE visitors            ADD COLUMN updated_by VARCHAR(100);
ALTER TABLE wristbands          ADD COLUMN created_by VARCHAR(100);
ALTER TABLE wristbands          ADD COLUMN updated_by VARCHAR(100);
ALTER TABLE tickets             ADD COLUMN created_by VARCHAR(100);
ALTER TABLE tickets             ADD COLUMN updated_by VARCHAR(100);
ALTER TABLE pass_types          ADD COLUMN created_by VARCHAR(100);
ALTER TABLE pass_types          ADD COLUMN updated_by VARCHAR(100);
ALTER TABLE pass_type_prices    ADD COLUMN created_by VARCHAR(100);
ALTER TABLE pass_type_prices    ADD COLUMN updated_by VARCHAR(100);
ALTER TABLE visits              ADD COLUMN created_by VARCHAR(100);
ALTER TABLE visits              ADD COLUMN updated_by VARCHAR(100);
ALTER TABLE access_entitlements ADD COLUMN created_by VARCHAR(100);
ALTER TABLE access_entitlements ADD COLUMN updated_by VARCHAR(100);
```

All columns are nullable. Pre-Phase 3 records will have `NULL` for both, which is correct: the actor is unknown because the security context did not exist when they were written.

The `AuditorAware<String>` implementation returns the JWT `sub` claim (username) when an authenticated request is in progress, and `Optional.empty()` otherwise (which leaves the field NULL for public endpoint calls like login).

---

## 8. Invariants and Validation Summary

| Entity/Rule | Invariant | Enforcement |
|---|---|---|
| StaffUser | `username` unique | DB UNIQUE constraint + 409 on duplicate |
| StaffUser | `username` immutable after creation | No PUT on username field; `updatable = false` at JPA level |
| StaffUser | `username` format `[a-z][a-z0-9._-]*`, 3–100 chars (must start with a letter) | Bean Validation (`@Pattern`) on `CreateStaffUserRequest` (400) |
| StaffUser | `password` at least 8 chars | Bean Validation on `CreateStaffUserRequest` (400); hashed before save |
| StaffUser | `roles` non-empty | Bean Validation `@NotEmpty` on request DTOs (400) |
| StaffUser | Active = false blocks login | Checked in `UserDetailsService` (returns disabled account → 401) |
| StaffUser | Admin cannot deactivate self | Service-layer check (422 `CANNOT_DEACTIVATE_SELF`) |
| JWT | HS256 signature + expiry | Spring Security's `JwtDecoder` (401 on failure) |
| 401/403 error format | Same `ErrorResponse` envelope as domain errors | Custom `AuthenticationEntryPoint` + `AccessDeniedHandler` |

---

## 9. Cross-Context Interactions

**Outgoing from Identity:** None. Identity is a provider only.

**Incoming to Identity (Phase 3.2 and later):**
- `SecurityConfig.userDetailsService(...)` calls `StaffUserDetailsService`, which calls `StaffUserRepository`. This is an intra-context call within the Security skeleton -- not a cross-context dependency.
- All other contexts declare `@PreAuthorize` role requirements. These annotations are evaluated by Spring's method security infrastructure using the `Authentication` object populated by the JWT filter from Identity's token.

**Actor auditing dependency:**
- `AuditorAware` reads from `SecurityContextHolder`. This is a framework-level cross-cutting concern, not a service-level cross-context call.

---

## 10. Phase 3 Sub-Phasing

Phase 3 is implemented in two sub-slices. Each follows: implementation → verification → adversarial review → commit.

1. **Phase 3.1 -- Identity (User Management):**
   - V007 migration (staff_users, staff_user_roles, seed admin)
   - `StaffUser` entity, `Role` enum
   - `StaffUserRepository`
   - `StaffUserService` (list, get, create, update roles, deactivate)
   - `StaffUserController` (`/api/identity/users`, `/api/identity/users/{id}`, `/api/identity/users/{id}/roles`, `/api/identity/users/{id}` DELETE)
   - DTOs: `CreateStaffUserRequest`, `UpdateStaffUserRolesRequest`, `StaffUserResponse`
   - Exceptions: `StaffUserNotFoundException`, `UsernameAlreadyExistsException`, `CannotDeactivateSelfException`
   - Tests: `StaffUserControllerTest` (`@WebMvcTest`), `StaffUserIntegrationTest` (`@SpringBootTest`)

2. **Phase 3.2 -- Security Skeleton:**
   - Add `spring-boot-starter-oauth2-resource-server` dependency (brings in `spring-security-oauth2-jose`)
   - Add `spring-security-test` to test dependencies
   - V008 migration (add `created_by`/`updated_by` to all BaseEntity tables)
   - Update `BaseEntity` with `@CreatedBy`/`@LastModifiedBy` fields
   - `JwtProperties` configuration record (`atpezms.jwt.secret`, `atpezms.jwt.expiryHours`)
   - `JwtService` (generate token, extract claims)
   - `StaffUserDetailsService` (implements `UserDetailsService`)
   - `CustomAuthenticationEntryPoint` (401 `UNAUTHORIZED`)
   - `CustomAccessDeniedHandler` (403 `FORBIDDEN`)
   - `SecurityConfig` (stateless, JWT filter, endpoint authorization map from §6)
   - `AuthController` (`POST /api/identity/login`)
   - DTOs: `LoginRequest`, `LoginResponse`
   - Exception: `InvalidCredentialsException` (401)
   - `AuditorAwareImpl` (reads from `SecurityContextHolder`)
   - Update all existing `@WebMvcTest` tests: add `@WithMockUser(roles = {...})` per endpoint role
   - Update all existing `@SpringBootTest` tests: add `.with(jwt().authorities(...))` per MockMvc request
   - Tests: `AuthControllerTest` (`@WebMvcTest`), `AuthIntegrationTest` (`@SpringBootTest`), security filter tests

---

## 11. Readiness For Subsequent Phases

After Phase 3.2:

- Every new controller method in Phase 4+ adds `// Requires: ROLE_...` comments which become `@PreAuthorize("hasRole('...')")` annotations immediately (no more placeholders -- security is live).
- `AuditorAware` is active: `createdBy`/`updatedBy` on all `BaseEntity`-extending entities are populated automatically.
- The `JwtService` is a standalone bean that can be used in tests (e.g., to generate a real test JWT for Phase 4+ integration tests that prefer a real token over the mock helper).
