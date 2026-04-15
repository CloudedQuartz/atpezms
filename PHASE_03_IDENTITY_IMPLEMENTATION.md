# ATPEZMS Phase 3 (Identity + Security Skeleton) -- Implementation Notes

This document is the **Level 2 implementation companion** for Phase 3 (Identity + Security Skeleton). It specifies the concrete Spring/JPA mechanics that bring `PHASE_03_IDENTITY_DESIGN.md` to life.

Cross-cutting rules (naming, DTO conventions, transaction placement, etc.) are in `IMPLEMENTATION.md`. This document records only Phase-3-specific decisions not already covered there.

---

## 1. New Exceptions

| Class | Extends | Error Code | HTTP |
|---|---|---|---|
| `StaffUserNotFoundException` | `ResourceNotFoundException` | `STAFF_USER_NOT_FOUND` | 404 |
| `UsernameAlreadyExistsException` | `DuplicateResourceException` | `USERNAME_ALREADY_EXISTS` | 409 |
| `CannotDeactivateSelfException` | `BusinessRuleViolationException` | `CANNOT_DEACTIVATE_SELF` | 422 |
| `InvalidCredentialsException` | `BaseException` (custom, 401) | `INVALID_CREDENTIALS` | 401 |

Note on `InvalidCredentialsException`: no category class in the current hierarchy maps to 401 (by design -- `DESIGN.md §3.2` reserves 401/403 strictly for auth). `InvalidCredentialsException` extends `BaseException` directly and overrides `getHttpStatus()` to return `HttpStatus.UNAUTHORIZED`. This is acceptable because `GlobalExceptionHandler.handleBaseException()` calls `ex.getHttpStatus()` polymorphically -- as long as the override is present, the correct 401 status is returned automatically. The pattern:

```java
public class InvalidCredentialsException extends BaseException {
    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Invalid credentials");
    }
    @Override public HttpStatus getHttpStatus() { return HttpStatus.UNAUTHORIZED; }
}
```

**Package:** `InvalidCredentialsException` lives in `com.atpezms.atpezms.common.exception` (NOT in `identity.exception`). Reason: `GlobalExceptionHandler` (in `common.exception`) must import this class. If it were in `identity.exception`, `common` would depend on a bounded context, inverting the intended dependency direction. `common` must not know about any specific bounded context.

**Additionally**, `AuthenticationException` thrown by `authManager.authenticate(...)` inside `AuthController` is NOT caught by `InvalidCredentialsException` (it is a Spring Security exception, not a `BaseException`). It bypasses the security filter's `AuthenticationEntryPoint` entirely because it is thrown from inside a controller method (after the filter chain has already passed). It falls through to `GlobalExceptionHandler.handleUnexpected(...)` and returns 500. This must be fixed by adding an explicit handler to `GlobalExceptionHandler`:

```java
// Import at the top of GlobalExceptionHandler:
// import org.springframework.security.core.AuthenticationException;

// Intentionally collapses ALL AuthenticationException subtypes (BadCredentialsException,
// DisabledException, LockedException, etc.) into a single 401 INVALID_CREDENTIALS response.
// Per design §2.6, this is deliberate: differentiating the failure reason would allow
// username enumeration and account-existence probing. If new auth exception types are added
// in future, they should map here too unless there is a compelling reason to differentiate.
//
// Note: Instant.now() is used here rather than the injectable Clock because
// GlobalExceptionHandler is not a service and does not have the domain Clock wired.
// The timestamp in error responses is best-effort wall-clock time; a 1-second drift
// is acceptable for error logging purposes.
@ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
public ResponseEntity<ErrorResponse> handleAuthentication(
        org.springframework.security.core.AuthenticationException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse(401, "INVALID_CREDENTIALS",
            "Invalid credentials", Instant.now(), List.of()));
}
```

This handler must be placed before the generic `Exception` handler in `GlobalExceptionHandler`.

All other exception classes live in `com.atpezms.atpezms.identity.exception`.

---

## 2. Gradle Dependency Changes

**Phase 3.1** -- No new dependencies. All Phase 3.1 code uses only JPA, Spring MVC, and validation, which are already present.

**Phase 3.2** -- Add two dependencies:

```groovy
// Spring Security + JWT (oauth2-resource-server brings spring-security-oauth2-jose
// which provides JwtEncoder and JwtDecoder backed by Nimbus JOSE+JWT)
implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'

// Spring Security Test utilities: @WithMockUser, SecurityMockMvcRequestPostProcessors.jwt(...)
testImplementation 'org.springframework.security:spring-security-test'
```

**Why `oauth2-resource-server` and not just `spring-security`?**
`spring-boot-starter-security` adds Spring Security without JWT support. The resource-server starter additionally brings `spring-security-oauth2-jose` (the Nimbus JOSE+JWT library repackaged under Spring) which provides `JwtEncoder` and `NimbusJwtDecoder`. Without this starter, we would have to declare the Nimbus dependency manually and wire the JWT beans ourselves. The resource-server starter does this automatically via auto-configuration that we can partially override.

---

## 3. Flyway Migrations

### V007 -- Staff users schema and seed admin

File: `V007__add_identity_staff_users.sql`

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

-- Default admin account. Password is BCrypt hash of "changeme" at cost 12.
-- Operators MUST change this password immediately in any real deployment.
-- The hash below is a PLACEHOLDER -- replace with the output of:
--   new BCryptPasswordEncoder(12).encode("changeme")
-- Run this once (e.g. in a small main method or unit test) and paste the result.
INSERT INTO staff_users (id, username, password_hash, full_name, active, created_at, updated_at)
VALUES (1, 'admin',
        '<bcrypt hash of "changeme" at cost 12 -- generate before implementation>',
        'System Administrator', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO staff_user_roles (staff_user_id, role) VALUES (1, 'ROLE_ADMIN');
```

**Generating the real hash at implementation time:**
```java
// Run once to get the hash; paste into V007 migration.
System.out.println(new BCryptPasswordEncoder(12).encode("changeme"));
// Output example format: $2a$12$<22-char-salt><31-char-hash>  (exactly 60 chars total)
```

A valid BCrypt hash is always **exactly 60 characters** in the format `$2a$12$<22 chars><31 chars>`. If the value in the migration is shorter or longer, it is invalid and `BCryptPasswordEncoder.matches()` will throw `IllegalArgumentException` at login time.

> **Education note on BCrypt cost factors:** BCrypt's cost factor `n` means `2^n` rounds of key-stretching (cost 12 = 4096 rounds). BCrypt **self-describes** its cost in the hash string (`$2a$12$...` means cost 12). This means verification always uses the cost from the hash, regardless of which cost the `BCryptPasswordEncoder` bean is configured with. You may safely verify a cost-12 hash with a `BCryptPasswordEncoder(10)` bean and it will still verify correctly — but for consistency, use the same `BCryptPasswordEncoder(12)` everywhere. 12 is a reasonable default as of 2026; increase it on credential rotation as hardware speeds up.

### V008 -- Actor audit columns on all BaseEntity tables

File: `V008__add_actor_audit_columns.sql`

```sql
-- H2 requires one column per ALTER TABLE statement (see IMPLEMENTATION.md §9.2).
-- Columns are nullable: pre-Phase 3 rows have NULL for actor, which is correct.
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

---

## 4. Entities

### 4.1 Role Enum

Package: `com.atpezms.atpezms.identity.entity`

```java
public enum Role {
    ROLE_ADMIN,
    ROLE_MANAGER,
    ROLE_TICKET_STAFF,
    ROLE_RIDE_OPERATOR,
    ROLE_FOOD_STAFF,
    ROLE_STORE_STAFF,
    ROLE_EVENT_COORDINATOR,
    ROLE_KIOSK
}
```

The enum values carry the `ROLE_` prefix so each value maps directly to a Spring Security `SimpleGrantedAuthority` string without transformation. When storing to the DB via `@ElementCollection`, they are stored as strings (e.g., `"ROLE_ADMIN"`).

### 4.2 StaffUser Entity

Package: `com.atpezms.atpezms.identity.entity`

```java
@Entity
@Table(name = "staff_users")
public class StaffUser extends BaseEntity {

    @Column(name = "username", nullable = false, length = 100, updatable = false)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "staff_user_roles",
        joinColumns = @JoinColumn(name = "staff_user_id")
    )
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    // Package-private constructor for JPA
    protected StaffUser() {}

    // Public constructor enforces invariants
    public StaffUser(String username, String passwordHash, String fullName, Set<Role> roles) { ... }

    // Mutation methods -- public so service classes in sibling packages can call them.
    // Package-private would not compile because StaffUserService is in identity.service
    // while StaffUser is in identity.entity -- different packages.
    public void updateRoles(Set<Role> newRoles) { this.roles = new HashSet<>(newRoles); }
    public void deactivate() { this.active = false; }
}
```

**Why `FetchType.EAGER` on roles?** Every time a `StaffUser` is loaded (which happens on every authenticated request via `UserDetailsService`), the roles are needed immediately to build the `GrantedAuthority` list. A lazy load would trigger an N+1 query per authentication. Since the roles collection is small (a user has at most a handful of roles), eager loading is the correct tradeoff.

**Why `updatable = false` on `username`?** Usernames appear in `createdBy`/`updatedBy` audit columns throughout the system. Allowing a username change would silently invalidate those historical audit records. The username is the stable audit identity; it must never change.

### 4.3 BaseEntity Update (Phase 3.2)

Add `@CreatedBy` and `@LastModifiedBy` to the existing `BaseEntity`:

```java
@CreatedBy
@Column(name = "created_by", updatable = false)
private String createdBy;

@LastModifiedBy
@Column(name = "updated_by")
private String updatedBy;
```

These fields are populated by Spring Data JPA auditing when `AuditorAware<String>` is configured.

---

## 5. Repositories

### 5.1 StaffUserRepository

```java
public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {
    Optional<StaffUser> findByUsername(String username);
    boolean existsByUsername(String username);
    List<StaffUser> findAllByOrderByUsernameAsc();
}
```

`findByUsername` is used by `StaffUserDetailsService` on every authentication attempt. The `username` column has an implicit index (a `UNIQUE` constraint always creates one in H2, PostgreSQL, and MySQL). The lookup is an **index seek / range scan** -- not a full table scan. Note: this is NOT an "index-only" (covering index) scan because all columns must be fetched from the heap; the index is used only to locate the row.

---

## 6. Services

### 6.1 StaffUserService (Phase 3.1)

Package: `com.atpezms.atpezms.identity.service`

Methods:

- `listUsers()` -- `@Transactional(readOnly = true)` -- returns `List<StaffUserResponse>` ordered by username.
- `getUser(Long id)` -- `@Transactional(readOnly = true)` -- throws `StaffUserNotFoundException`.
- `createUser(CreateStaffUserRequest)` -- `@Transactional` -- hashes the password with `BCryptPasswordEncoder`, checks uniqueness, saves, returns `StaffUserResponse`. Catches `DataIntegrityViolationException` for TOCTOU uniqueness (same pattern as Zone CRUD).
- `updateRoles(Long id, UpdateStaffUserRolesRequest)` -- `@Transactional` -- throws `StaffUserNotFoundException`.
- `deactivateUser(Long id, String requestingUsername)` -- `@Transactional` -- checks the self-deactivation guard, sets `active = false`.

### 6.2 JwtService (Phase 3.2)

Package: `com.atpezms.atpezms.identity.service`

Responsible for JWT generation and claim extraction.

```java
@Service
public class JwtService {

    /**
     * Return value from generateToken -- pairs the compact token string with the
     * exact expiry Instant embedded in the JWT's exp claim.
     *
     * <p>Declared as a nested static record inside JwtService so callers use the
     * qualified name {@code JwtService.GeneratedToken} unambiguously.
     * (A top-level class named {@code GeneratedToken} in the same package would
     * require an unqualified reference and cannot use the dot notation.)
     */
    public static record GeneratedToken(String token, Instant expiresAt) {}

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;  // injectable Clock per IMPLEMENTATION.md §7.1

    // Accepts (String username, Set<String> roles) -- NOT StaffUser entity.
    // The AuthController has already extracted this data from the Authentication object,
    // eliminating any second DB query or TOCTOU window.
    public GeneratedToken generateToken(String username, Set<String> roles) {
        Instant now = Instant.now(clock);
        Instant expiry = now.plusSeconds(properties.expiryHours() * 3600L);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(username)
            .issuedAt(now)
            .expiresAt(expiry)
            .claim("roles", roles.stream().sorted().toList()) // sorted for determinism
            .build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new GeneratedToken(token, expiry);
    }
}
```

> **Education note on `JwsHeader` vs `JwtHeader`:** `JwsHeader` (JSON Web Signature Header) represents the header of a _signed_ JWT. The `MacAlgorithm.HS256` specifies the HMAC-SHA256 algorithm. `JwtEncoder.encode` takes both the header and the claims; the Nimbus implementation serializes both, base64url-encodes them, computes the HMAC signature, and concatenates all three parts with dots.

> **Why `GeneratedToken` instead of returning just the token string?** `AuthController` needs to return `expiresAt` in `LoginResponse`. Computing `expiresAt` independently with a second `Instant.now()` call would produce a value that may differ by up to 1 second from the `exp` claim actually embedded in the JWT (due to GC pauses or clock progression between the two calls). Returning both values from the same `generateToken()` call ensures the client's `expiresAt` exactly matches the token's real expiry.

> **Why `Clock` injection?** Per `IMPLEMENTATION.md §7.1`, services that depend on "now" should accept an injectable `Clock` bean. This makes the login endpoint testable for time-sensitive assertions (e.g., verify `expiresAt` is exactly 8 hours after issue time) without needing `Thread.sleep` in tests.

### 6.3 StaffUserDetailsService (Phase 3.2)

Package: `com.atpezms.atpezms.identity.service`

Implements Spring Security's `UserDetailsService`. Called by Spring's `DaoAuthenticationProvider` during the login process to load the user from the database.

```java
@Service
public class StaffUserDetailsService implements UserDetailsService {

    // Pre-computed BCrypt hash used for dummy verification when the user is not found.
    // Prevents username enumeration via response-time difference (see design §2.6).
    //
    // This MUST be a structurally valid BCrypt hash (exactly 60 chars, format $2a$12$<22><31>).
    // An invalid hash causes BCryptPasswordEncoder.matches() to throw IllegalArgumentException,
    // which propagates as a 500-class error, creating an exploitable error-path distinction.
    //
    // Generate the real value at implementation time with:
    //   new BCryptPasswordEncoder(12).encode("some-random-string-that-will-never-be-a-password")
    // Paste the 60-character output below.
    private static final String DUMMY_HASH =
        "<valid BCrypt cost-12 hash of any random string -- generate before implementation>";

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return staffUserRepository.findByUsername(username)
            .map(user -> User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!user.isActive())
                .authorities(user.getRoles().stream()
                    .map(r -> new SimpleGrantedAuthority(r.name()))
                    .toList())
                .build())
            .orElseGet(() -> {
                // Run a dummy BCrypt verification to equalize response time whether or
                // not the username exists. Without this, a missing username returns in ~0ms
                // (no hash needed), while a wrong password returns in ~250ms (BCrypt runs).
                // An attacker can enumerate valid usernames by measuring response time.
                passwordEncoder.matches("dummy", DUMMY_HASH);
                throw new UsernameNotFoundException("User not found");
                // Note: no username in the message -- prevents leaking valid usernames in logs.
            });
    }
}
```

When `active = false`, Spring Security's `AccountStatusUserDetailsChecker` (called internally by `DaoAuthenticationProvider`) throws `DisabledException`. The `GlobalExceptionHandler.handleAuthentication(AuthenticationException)` handler (see §1) maps this to 401 `INVALID_CREDENTIALS` -- same as wrong password, no differentiation per design §2.6.

---

## 7. Controllers

### 7.1 StaffUserController (Phase 3.1)

Package: `com.atpezms.atpezms.identity.controller`

Base path: `/api/identity/users`

Endpoints:
- `GET /api/identity/users` → `listUsers()`
- `GET /api/identity/users/{id}` → `getUser(id)`
- `POST /api/identity/users` → `createUser(@RequestBody @Valid CreateStaffUserRequest)`
- `PUT /api/identity/users/{id}/roles` → `updateRoles(id, @RequestBody @Valid UpdateStaffUserRolesRequest)`
- `DELETE /api/identity/users/{id}` → `deactivateUser(id)` -- extracts `requestingUsername` from `Authentication` principal (available in Phase 3.2; in Phase 3.1, the guard is implemented but the principal is not yet available from a JWT -- the controller method will have an `Authentication authentication` parameter that is `null` in Phase 3.1 and populated in Phase 3.2).

Phase 3.1 implementation note for the self-deactivation guard: the guard requires knowing the requesting user's username. In Phase 3.1, before security is active, pass `null` as the requesting username -- the guard skips the check when the requesting username is null. In Phase 3.2, wire the `Authentication` object.

### 7.2 AuthController (Phase 3.2)

Package: `com.atpezms.atpezms.identity.controller`

```java
@RestController
@RequestMapping("/api/identity")
public class AuthController {

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        // authenticate() uses AuthenticationManager which calls StaffUserDetailsService
        // and BCryptPasswordEncoder internally. Throws AuthenticationException on failure.
        // That exception is caught by GlobalExceptionHandler.handleAuthentication() → 401.
        Authentication auth = authManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        // Use authorities already in the Authentication object -- avoids a second DB query
        // and eliminates the TOCTOU window where an admin could deactivate the user between
        // the authenticate() call and a re-fetch.
        Set<String> roles = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
        // generateToken returns both the token string and the exact expiry Instant embedded
        // in the JWT, so LoginResponse.expiresAt exactly matches the token's exp claim.
        JwtService.GeneratedToken generated = jwtService.generateToken(auth.getName(), roles);
        List<String> sortedRoles = roles.stream().sorted().toList();
        return ResponseEntity.ok(new LoginResponse(
            generated.token(), generated.expiresAt(), auth.getName(), sortedRoles));
    }
}
```

The `AuthenticationManager` handles the credential check. The controller does not call BCrypt directly -- it delegates to Spring Security's internal machinery. `JwtService.generateToken(String username, Set<String> roles)` accepts the already-extracted principal data, not a `StaffUser` entity -- this avoids an extra DB query and the TOCTOU window described in the design.

On `AuthenticationException` (bad credentials, disabled account): `GlobalExceptionHandler.handleAuthentication(AuthenticationException)` (see §1) returns 401 `INVALID_CREDENTIALS` with the same message regardless of the failure type. This exception is thrown from inside the controller, which means the security filter's `AuthenticationEntryPoint` is NOT involved -- the handler in `GlobalExceptionHandler` is the only thing that catches it.

---

## 8. Security Configuration (Phase 3.2)

### 8.1 SecurityConfig

Package: `com.atpezms.atpezms.common.config`

Why `common.config`? `SecurityConfig` is cross-cutting: it applies rules across all contexts. It belongs in `common.config` alongside `JpaAuditConfig` and `ClockConfig`.

Key decisions encoded in the filter chain:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // enables @PreAuthorize on service/controller methods
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)           // §2.8: JWT API, no cookies
            .sessionManagement(s ->
                s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // §2.8
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(POST, "/api/identity/login").permitAll()  // §6 public
                .anyRequest().authenticated())               // everything else needs JWT
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt -> jwt               // validates Bearer token
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter()))) // reads "roles" claim
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(customEntryPoint)  // §2.7: 401 response
                .accessDeniedHandler(customAccessDeniedHandler)); // §2.7: 403 response
        return http.build();
    }
}
```

**Why `anyRequest().authenticated()` instead of explicit endpoint rules in `SecurityFilterChain`?**

We use `@PreAuthorize` on individual controller methods to enforce roles (e.g., `@PreAuthorize("hasRole('ADMIN')")`). The filter chain only enforces "must be authenticated" as a baseline. This is the recommended Spring Security 6/7 pattern: the filter chain handles authentication, method security handles authorization. The split avoids duplicating URL patterns in two places.

**Why `@EnableMethodSecurity` instead of `@EnableGlobalMethodSecurity`?**

`@EnableGlobalMethodSecurity` is deprecated in Spring Security 6+. `@EnableMethodSecurity` is its replacement and is required to activate `@PreAuthorize` processing.

### 8.2 JwtDecoder and JwtEncoder Beans

```java
@Bean
public JwtDecoder jwtDecoder() {
    SecretKeySpec key = new SecretKeySpec(
        Base64.getDecoder().decode(properties.secret()), "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
}

@Bean
public JwtEncoder jwtEncoder() {
    SecretKeySpec key = new SecretKeySpec(
        Base64.getDecoder().decode(properties.secret()), "HmacSHA256");
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
}
```

The same secret key is used for both encoding (signing) and decoding (verifying) -- this is the HS256 symmetric model. Both beans decode the same Base64-encoded property.

> **Education note:** `NimbusJwtDecoder.withSecretKey(key)` internally creates a `MacVerifier` backed by the secret. When a request arrives with a JWT, the decoder splits the token at dots, base64-decodes header and payload, recomputes the HMAC of `header.payload` with the key, and compares to the decoded signature. If they match, the claims are returned. If not, a `JwtValidationException` is thrown.

### 8.3 Roles Claim Converter

Spring Security's default JWT converter reads the `scope` claim for authorities. Our JWTs use a custom `roles` claim. A `JwtAuthenticationConverter` must be configured to read it:

```java
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter =
        new JwtGrantedAuthoritiesConverter();
    grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
    grantedAuthoritiesConverter.setAuthorityPrefix(""); // roles already have ROLE_ prefix
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
    return converter;
}
```

**Critical:** this converter MUST be wired into the resource server configuration (see §8.1 filterChain snippet). If omitted, Spring Security uses its default converter which reads the `scope` or `scp` claim with a `SCOPE_` prefix. Since our tokens carry a `roles` claim with `ROLE_` prefix, every `@PreAuthorize("hasRole('ADMIN')")` check will return 403 for every valid token -- silently, with no error message that explains why.

### 8.4 AuthenticationManager Bean

`AuthenticationManager` is not auto-exposed as a bean in Spring Security 6+. It must be explicitly declared:

```java
@Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
        throws Exception {
    return config.getAuthenticationManager();
}
```

The `AuthController` injects this bean to call `.authenticate(UsernamePasswordAuthenticationToken)`.

### 8.5 PasswordEncoder Bean

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
}
```

Declared as a bean so it can be injected into both `StaffUserService` (for hashing on creation) and Spring Security's `DaoAuthenticationProvider` (for verification during login). Declaring it as a bean avoids constructing multiple `BCryptPasswordEncoder` instances.

### 8.6 JWT Key Length Validation at Startup

Following the pattern in `IMPLEMENTATION.md §13.4` (PII encryption key validation), the `JwtService` must validate the secret key length at startup via `@PostConstruct`:

```java
@PostConstruct
void validateSecret() {
    byte[] keyBytes = Base64.getDecoder().decode(properties.secret());
    if (keyBytes.length < 32) {
        throw new IllegalStateException(
            "atpezms.jwt.secret must be at least 256 bits (32 bytes Base64-encoded). " +
            "Got " + keyBytes.length + " bytes. Generate with: openssl rand -base64 32");
    }
}
```

For HS256, NIST SP 800-107 requires the key to be at least as long as the hash output (256 bits = 32 bytes). A shorter key is technically accepted by the crypto library but is cryptographically weak. Failing fast at startup ensures misconfiguration is caught immediately, not silently at the first JWT signing attempt.

---

## 9. AuditorAware (Phase 3.2)

Package: `com.atpezms.atpezms.common.config`

```java
@Component
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        // Filter out AnonymousAuthenticationToken: Spring Security sets a default
        // AnonymousAuthenticationToken (name = "anonymousUser") for all unauthenticated
        // requests. Without this filter, createdBy/updatedBy would be "anonymousUser"
        // during Phase 3.1 (before security is enforced), not NULL as intended.
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .filter(a -> a.isAuthenticated()
                && !(a instanceof AnonymousAuthenticationToken))
            .map(Authentication::getName);
    }
}
```

After Phase 3.2, `Authentication::getName` returns the JWT `sub` claim (the username). During Phase 3.1 (no Spring Security on classpath yet), `SecurityContextHolder.getContext().getAuthentication()` is `null`, so `getCurrentAuditor()` returns `Optional.empty()` and `createdBy`/`updatedBy` stay `NULL`. Once Phase 3.2 dependency is added, the `AnonymousAuthenticationToken` filter ensures the same `NULL` behavior for unauthenticated paths (i.e., the login endpoint itself).

Update `JpaAuditConfig` to wire this. **The complete, updated annotation must preserve all existing attributes:**

```java
@Configuration
@EnableJpaAuditing(
    modifyOnCreate = true,           // pre-existing: ensures updatedAt is non-null on INSERT
    dateTimeProviderRef = "auditDateTimeProvider",  // pre-existing: UTC Instant timestamps
    auditorAwareRef = "auditorAwareImpl"  // new in Phase 3.2: actor audit
)
public class JpaAuditConfig { ... }
```

**Dropping `modifyOnCreate = true`** would cause `@LastModifiedDate` (`updatedAt`) to be `null` on first insert, violating the `NOT NULL` constraint on `updated_at` in every table -- every entity save would fail with `DataIntegrityViolationException`. The full annotation must be written each time the class is edited.

> **Education note:** `@EnableJpaAuditing` uses the `auditorAwareRef` to find the bean responsible for answering "who is acting?". Spring Data JPA calls `getCurrentAuditor()` inside the `@PrePersist` and `@PreUpdate` lifecycle callbacks (the same callbacks that populate `createdAt`/`updatedAt`). Without wiring `auditorAwareRef`, actor fields would always be null even with `AuditorAware` present.

---

## 10. DTOs

### Phase 3.1

**`CreateStaffUserRequest`:**
```java
public record CreateStaffUserRequest(
    @NotBlank @Size(min = 3, max = 100) @Pattern(regexp = "[a-z][a-z0-9._-]*") String username,
    @NotBlank @Size(min = 8) String password,
    @NotBlank @Size(max = 200) String fullName,
    @NotEmpty Set<Role> roles
) {}
```

**`UpdateStaffUserRolesRequest`:**
```java
public record UpdateStaffUserRolesRequest(
    @NotEmpty Set<Role> roles
) {}
```

**`StaffUserResponse`:**
```java
public record StaffUserResponse(
    Long id,
    String username,
    String fullName,
    boolean active,
    List<String> roles,   // sorted list of role name strings, e.g. ["ROLE_ADMIN", "ROLE_MANAGER"]
    Instant createdAt,
    Instant updatedAt
) {
    public static StaffUserResponse from(StaffUser user) {
        return new StaffUserResponse(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.isActive(),
            user.getRoles().stream().map(Role::name).sorted().toList(), // deterministic order
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
```

Notes:
- `passwordHash` is **never** included in `StaffUserResponse`. Excluding it from the DTO guarantees it can never accidentally leak regardless of how the response is serialized.
- Roles are returned as `List<String>` (sorted ascending) rather than `Set<Role>`. `Set` has non-deterministic JSON serialization order, which makes test assertions such as `jsonPath("$.roles[0]").value("ROLE_ADMIN")` flaky across JVM runs. A sorted `List<String>` produces a stable JSON array.

### Phase 3.2

**`LoginRequest`:**
```java
public record LoginRequest(
    @NotBlank String username,
    @NotBlank String password
) {}
```

**`LoginResponse`:**
```java
public record LoginResponse(
    String token,
    Instant expiresAt,
    String username,
    List<String> roles   // sorted ascending for deterministic JSON array order
) {}
```

---

## 11. Test Strategy

### 11.1 Phase 3.1 Tests (Before Security Is Active)

Before Phase 3.2, no security filter exists. Controller and integration tests call endpoints freely.

**`StaffUserControllerTest` (`@WebMvcTest`):**
- Mocks `StaffUserService`.
- Tests: list, get, create (success, 409 duplicate, 400 missing fields), update roles (success, 404), deactivate (success, 404).
- The `422 CANNOT_DEACTIVATE_SELF` case requires a populated `Authentication` principal, which is only available after Phase 3.2 security is active. **Defer this test case to Phase 3.2.**

**`StaffUserIntegrationTest` (`@SpringBootTest`):**
- Full stack against H2 with `@ActiveProfiles("test")` and `@Transactional`.
- Tests: end-to-end CRUD, password hashing (verify hash stored, not plaintext), role update persistence.

### 11.2 Phase 3.2 Tests (Security Active)

**New tests:**

**`AuthControllerTest` (`@WebMvcTest(AuthController.class)`):**
- Tests: 200 + token on valid credentials; 401 `INVALID_CREDENTIALS` on wrong password; 401 on deactivated account; 400 `VALIDATION_FAILED` on missing fields.

**`AuthIntegrationTest` (`@SpringBootTest`):**
- Uses the seeded admin account from V007.
- Tests: login returns valid JWT; expired token returns 401; wrong password returns 401; decoded token contains correct claims.

**Security filter integration tests:**
- A protected endpoint (`GET /api/park/zones`) returns 401 without a token.
- The same endpoint returns 403 with a valid token but wrong role.
- The same endpoint returns 200 with the correct role.

**Updating all existing `@WebMvcTest` tests:**

Add `@WithMockUser(roles = {...})` at the method or class level. The role used depends on the endpoint's `// Requires:` comment.

> **Warning:** `@WithMockUser` without a `roles =` parameter defaults to `roles = {"USER"}`, which maps to `ROLE_USER`. No application endpoint accepts `ROLE_USER`. Tests that omit `roles =` will silently get 403 instead of the expected 200 -- the test will fail but the error message says "expected 200, got 403" with no obvious indication why. Always specify the exact required role.

Example (ZoneControllerTest):
```java
@Test
@WithMockUser(roles = "MANAGER")  // matches "// Requires: ROLE_MANAGER or ROLE_ADMIN"
void shouldReturnZonesOrderedByCode() throws Exception { ... }
```

**`@WebMvcTest` and `@EnableMethodSecurity`:** `@WebMvcTest` loads only the web slice -- it does NOT automatically load `@Configuration` classes that are not part of Spring Security's auto-configuration. `SecurityConfig` (a user-defined `@Configuration`) must be explicitly imported for `@PreAuthorize` annotations to be active in controller tests:

```java
@WebMvcTest(ZoneController.class)
@Import(SecurityConfig.class)  // required for @PreAuthorize to fire
class ZoneControllerTest { ... }
```

Without `@Import(SecurityConfig.class)`, method security is disabled in the test slice: a controller method annotated with `@PreAuthorize("hasRole('ADMIN')")` will pass even with a `ROLE_USER` mock user -- giving false confidence in your authorization tests. Tests that only check the happy path (correct role → 200) will still pass; tests that check unauthorized access (wrong role → 403) will fail or produce 200.

**Updating all existing `@SpringBootTest` integration tests:**

Add `.with(SecurityMockMvcRequestPostProcessors.jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))` to each `mockMvc.perform(...)` call.

Example (ZoneIntegrationTest):
```java
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

mockMvc.perform(get("/api/park/zones")
        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
    .andExpect(status().isOk());
```

> **Education note on `SecurityMockMvcRequestPostProcessors.jwt(...)`:** This helper injects a synthetic `JwtAuthenticationToken` directly into the request's security context, bypassing the JWT filter entirely. It does not generate or sign a real JWT. This is correct for integration tests because they are testing business logic, not the JWT signing/verification mechanism. The signing/verification is tested separately in `AuthIntegrationTest`.

---

## 12. Step Plan

### Phase 3.1 Steps

1. Write V007 migration.
2. Add `Role` enum.
3. Add `StaffUser` entity.
4. Add `StaffUserRepository`.
5. Add DTOs: `CreateStaffUserRequest`, `UpdateStaffUserRolesRequest`, `StaffUserResponse`.
6. Add exceptions: `StaffUserNotFoundException`, `UsernameAlreadyExistsException`, `CannotDeactivateSelfException`.
7. Add `StaffUserService` (inject `PasswordEncoder` -- provide it as a `@Bean` in `SecurityConfig` placeholder or temporary `@Configuration` so Phase 3.1 can compile without the full `SecurityConfig`).
8. Add `StaffUserController`.
9. Write `StaffUserControllerTest` and `StaffUserIntegrationTest`.
10. Run `./gradlew test`. Fix any issues.
11. Adversarial review.
12. Commit.

### Phase 3.2 Steps

> **Important ordering note:** The moment `spring-boot-starter-oauth2-resource-server` is added in step 1, Spring Security auto-configuration activates a default HTTP Basic filter that blocks all requests with 401. Every existing test in the project will fail immediately. Steps 1 and 2 (adding the dependency + updating all existing tests) **must be treated as a single atomic unit** -- do them together before running the test suite. The build will be entirely broken between these two steps.

1. Add dependencies to `build.gradle` (resource-server, security-test). **Immediately proceed to step 2 without running tests.** Also replace both placeholder hashes in V007 (`DUMMY_HASH` in `StaffUserDetailsService`, admin seed in V007) with real BCrypt hashes generated with `new BCryptPasswordEncoder(12).encode(...)`.
2. Update all existing `@WebMvcTest` tests with `@WithMockUser(roles = {...})` + `@Import(SecurityConfig.class)`.
3. Update all existing `@SpringBootTest` integration tests with `.with(jwt().authorities(...))`.
4. Write V008 migration.
5. Update `BaseEntity` with `createdBy`/`updatedBy` fields.
6. Add `JwtProperties` configuration record (wired via `@ConfigurationProperties`). Add JWT key to `application-test.properties` and `application-dev.properties` (same pattern as the encryption key).
7. Add `JwtService` (`generateToken(String username, Set<String> roles)` with nested `GeneratedToken` record, startup key length validation via `@PostConstruct` -- see §8.6). **Before writing V007, ensure the `DUMMY_HASH` placeholder in `StaffUserDetailsService` is replaced with a real BCrypt hash generated with `new BCryptPasswordEncoder(12).encode("some-random-string")`.**
8. Add `StaffUserDetailsService` (with dummy BCrypt for timing defense -- see §6.3).
9. Add `CustomAuthenticationEntryPoint` and `CustomAccessDeniedHandler`.
10. Add `SecurityConfig` with filter chain, JwtDecoder, JwtEncoder, AuthenticationManager, PasswordEncoder, roles converter (wired in filterChain -- critical, see §8.3).
11. Update `JpaAuditConfig` (preserve `modifyOnCreate = true` + `dateTimeProviderRef` + add `auditorAwareRef`).
12. Add `AuditorAwareImpl` (with `AnonymousAuthenticationToken` filter -- see §9).
13. Add `InvalidCredentialsException`; add `AuthenticationException` handler in `GlobalExceptionHandler`.
14. Add `LoginRequest`, `LoginResponse` DTOs.
15. Add `AuthController`.
16. Add `@PreAuthorize` annotations to all existing controllers (per §6 of design).
17. Harden the self-deactivation null-path in `StaffUserService.deactivateUser`: after Phase 3.2 security is active, `requestingUsername` should never be `null` (the filter rejects unauthenticated requests before reaching the controller). Replace the `null`-skip with an `IllegalStateException` (or assert non-null) so a missing principal fails loudly rather than silently bypassing the guard.
18. Add `422 CANNOT_DEACTIVATE_SELF` test case to `StaffUserControllerTest` (deferred from Phase 3.1).
19. Write `AuthControllerTest`, `AuthIntegrationTest`, security filter integration tests.
20. Run `./gradlew test`. Fix any issues.
21. Adversarial review.
22. Commit.

> **Migration rollback note:** Flyway does not support rollbacks by default. If V007 or V008 is applied and found to need a fix during development: (a) for the H2 in-memory test database -- just fix the migration file and restart (the in-memory DB is rebuilt from scratch on every test run); (b) for the H2 file dev database -- delete `./data/atpezms-dev.mv.db` and restart (all migrations re-run from scratch). Never edit a migration file that has been applied to a persistent database -- Flyway will reject it with a checksum mismatch error. Instead, write a new `V00N+1` migration to correct the schema.
