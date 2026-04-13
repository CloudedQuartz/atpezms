package com.atpezms.atpezms.ticketing.entity;

import com.atpezms.atpezms.common.converter.StringEncryptionConverter;
import com.atpezms.atpezms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * A person who has visited or is registered to visit the park.
 *
 * <h2>Persistence across visits</h2>
 * A Visitor row persists between park entries. A returning visitor is the
 * <em>same</em> Visitor row linked to a new Wristband and Visit on each
 * subsequent entry. This means a visitor's history is fully traceable without
 * duplicating personal data.
 *
 * <h2>PII fields and encryption</h2>
 * The spec (SE-1, CO-3) requires visitor PII to be encrypted at rest.
 * Fields classified as PII here:
 * <ul>
 *   <li>{@code firstName} / {@code lastName} -- legal name, directly identifying.</li>
 *   <li>{@code email} -- contact information, directly identifying.</li>
 *   <li>{@code phone} -- contact information, directly identifying.</li>
 * </ul>
 *
 * Each of these is annotated with {@code @Convert(converter = StringEncryptionConverter.class)}.
 * Hibernate calls the converter transparently before every INSERT/UPDATE and
 * after every SELECT, so the rest of the application always sees plain Strings
 * while the database columns ({@code *_enc}) hold ciphertext.
 *
 * The {@code _enc} suffix on the column names is a deliberate convention so
 * that anyone reading the schema or a database dump immediately knows those
 * columns are encrypted and must not be interpreted as plaintext.
 *
 * <h2>Non-PII operational fields</h2>
 * {@code dateOfBirth} and {@code heightCm} are stored as plain values.
 * Rationale (from {@code PHASE_01_TICKETING_DESIGN.md} §3.2):
 * <ul>
 *   <li>Ride eligibility checks (minimum height, minimum age) run on the hot
 *       path (PR-1 &lt; 1 second). Decrypting these fields on every scan would
 *       add latency and complexity without a clear security benefit, since they
 *       cannot be used to identify a visitor without a name or contact detail.</li>
 *   <li>Pricing calculations need age on every ticket issuance.</li>
 * </ul>
 * Both fields are still treated as sensitive: they must never appear in logs.
 *
 * <h2>Why the protected no-arg constructor?</h2>
 * JPA requires a no-argument constructor so it can instantiate entities via
 * reflection when hydrating rows from a SELECT result. We mark it
 * {@code protected} (not {@code public}) to make it clear that this constructor
 * is for JPA's use only -- application code should use the full constructor.
 * Making it {@code private} would also work for most JPA implementations but
 * {@code protected} is the safer, spec-compliant choice.
 *
 * <h2>@PrePersist / @PreUpdate validation</h2>
 * JPA lifecycle callbacks annotated with {@code @PrePersist} fire immediately
 * before an INSERT, and {@code @PreUpdate} fires immediately before an UPDATE.
 * Because the no-arg constructor bypasses our full constructor's argument
 * checks, we add a {@code validate()} method annotated with both callbacks.
 * This ensures that even if a Visitor entity is somehow constructed in an
 * invalid state (e.g. via a framework or test that calls the no-arg constructor
 * and then sets fields), the invalid state is caught before it reaches the
 * database, not silently persisted.
 */
@Entity
@Table(name = "visitors")
public class Visitor extends BaseEntity {

    /**
     * Legal first name. PII -- encrypted at rest.
     * Column {@code first_name_enc} stores the Base64-encoded ciphertext.
     */
    @Convert(converter = StringEncryptionConverter.class)
    @Column(name = "first_name_enc", nullable = false, length = 512)
    private String firstName;

    /**
     * Legal last name. PII -- encrypted at rest.
     */
    @Convert(converter = StringEncryptionConverter.class)
    @Column(name = "last_name_enc", nullable = false, length = 512)
    private String lastName;

    /**
     * Email address. PII -- encrypted at rest. Optional (nullable).
     */
    @Convert(converter = StringEncryptionConverter.class)
    @Column(name = "email_enc", length = 512)
    private String email;

    /**
     * Phone number. PII -- encrypted at rest. Optional (nullable).
     */
    @Convert(converter = StringEncryptionConverter.class)
    @Column(name = "phone_enc", length = 512)
    private String phone;

    /**
     * Date of birth. Non-PII operational field stored plain.
     * Used for pricing (age-group calculation) and ride eligibility.
     * Must never appear in application logs.
     */
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    /**
     * Height in centimetres. Non-PII operational field stored plain.
     * Used for ride eligibility checks (SF-2 minimum height requirement).
     * Must never appear in application logs.
     */
    @Column(name = "height_cm", nullable = false)
    private int heightCm;

    /**
     * For JPA reflection-based instantiation only.
     * Application code must use the full constructor.
     */
    protected Visitor() {}

    /**
     * Creates a valid Visitor. All required fields are validated before the
     * object is constructed; an invalid argument throws immediately rather
     * than waiting until the first database write.
     *
     * @param firstName  legal first name; must not be blank
     * @param lastName   legal last name; must not be blank
     * @param email      email address; nullable
     * @param phone      phone number; nullable
     * @param dateOfBirth date of birth; must not be null and must be in the past
     * @param heightCm   height in cm; must be positive
     */
    public Visitor(String firstName, String lastName, String email, String phone,
                   LocalDate dateOfBirth, int heightCm) {
        requireNonBlank(firstName, "firstName");
        requireNonBlank(lastName, "lastName");
        if (dateOfBirth == null) {
            throw new IllegalArgumentException("dateOfBirth is required");
        }
        // LocalDate.now(ZoneOffset.UTC) is used deliberately throughout.
        // Using LocalDate.now() (JVM default timezone) is a latent bug: a server
        // running UTC would reject a visitor born "today" in a timezone ahead of
        // UTC (e.g. UTC+5:30) as "in the future" for part of the day. UTC is the
        // correct anchor for a server-side business rule; the park's local calendar
        // can be layered on top if required in a future slice.
        if (dateOfBirth.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new IllegalArgumentException("dateOfBirth must not be in the future");
        }
        if (heightCm <= 0) {
            throw new IllegalArgumentException("heightCm must be positive");
        }
        this.firstName   = firstName;
        this.lastName    = lastName;
        this.email       = email;
        this.phone       = phone;
        this.dateOfBirth = dateOfBirth;
        this.heightCm    = heightCm;
    }

    // -----------------------------------------------------------------------
    // JPA lifecycle validation
    // -----------------------------------------------------------------------

    /**
     * Fires before every INSERT and UPDATE.
     *
     * This duplicates the constructor checks intentionally. JPA creates entity
     * instances via the no-arg constructor (reflection) when hydrating a
     * SELECT result, bypassing the full constructor entirely. These callbacks
     * guard against a different threat: code that constructs a Visitor with the
     * no-arg constructor and then sets fields directly (e.g. a faulty test
     * helper or a future subclass). Without this guard, such a Visitor could be
     * persisted with null or blank required fields.
     */
    @PrePersist
    @PreUpdate
    private void validate() {
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalStateException("Visitor.firstName must not be blank");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalStateException("Visitor.lastName must not be blank");
        }
        if (dateOfBirth == null) {
            throw new IllegalStateException("Visitor.dateOfBirth is required");
        }
        // Mirror the constructor's future-date check so entities constructed
        // via the JPA no-arg constructor cannot be persisted with a future date.
        // Uses UTC for the same reasons documented in the constructor.
        if (dateOfBirth.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new IllegalStateException("Visitor.dateOfBirth must not be in the future");
        }
        if (heightCm <= 0) {
            throw new IllegalStateException("Visitor.heightCm must be positive");
        }
    }

    // -----------------------------------------------------------------------
    // Accessors (read-only; no setters -- Visitor fields are immutable once
    // registered; updates require a deliberate service operation, not a setter)
    // -----------------------------------------------------------------------

    public String getFirstName()   { return firstName;   }
    public String getLastName()    { return lastName;    }
    public String getEmail()       { return email;       }
    public String getPhone()       { return phone;       }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public int getHeightCm()       { return heightCm;    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
