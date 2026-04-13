package com.atpezms.atpezms.ticketing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Request DTO for {@code POST /api/ticketing/visitors}.
 *
 * <h2>Why a record?</h2>
 * Java records are immutable data carriers. A request DTO should not be
 * mutable after deserialization -- using a record enforces this structurally.
 * The compact constructor form also keeps the class concise.
 *
 * <h2>Why validation annotations on the DTO and not the entity?</h2>
 * Validation annotations on a DTO enforce the shape of the incoming HTTP
 * request at the web boundary, before any business logic runs. The entity
 * enforces domain invariants at the persistence boundary. This two-layer
 * approach catches most errors early (wrong type, missing field, length
 * violation) and reserves the entity's invariant checks for domain-logic
 * violations that cannot be expressed with bean validation constraints
 * (e.g., "date of birth must not be in the future" vs. {@code @Past}).
 *
 * <h2>@Valid and automatic validation</h2>
 * When a controller parameter is annotated with {@code @Valid}, Spring MVC
 * runs Jakarta Bean Validation on the deserialized object before invoking
 * the controller method. Any violation produces a
 * {@code MethodArgumentNotValidException}, which the global exception
 * handler maps to a 400 response with a structured list of field errors.
 * The controller never needs to call {@code validator.validate()} manually.
 */
public record CreateVisitorRequest(

        /**
         * Legal first name. Required; max 100 characters.
         * Length bound matches the entity's column definition.
         */
        @NotBlank(message = "firstName is required")
        @Size(max = 100, message = "firstName must not exceed 100 characters")
        String firstName,

        /**
         * Legal last name. Required; max 100 characters.
         */
        @NotBlank(message = "lastName is required")
        @Size(max = 100, message = "lastName must not exceed 100 characters")
        String lastName,

        /**
         * Email address. Optional (nullable). When provided, must be a valid
         * email format and no longer than 200 characters of plaintext.
         * The encrypted ciphertext will be longer, but that is bounded by the
         * VARCHAR(512) column definition.
         */
        @Email(message = "email must be a valid email address")
        @Size(max = 200, message = "email must not exceed 200 characters")
        String email,

        /**
         * Phone number. Optional (nullable). Free-form string to accommodate
         * international formats (e.g. +94 77 123 4567).
         */
        @Size(max = 30, message = "phone must not exceed 30 characters")
        String phone,

        /**
         * Date of birth. Required. Must be in the past (yesterday or earlier).
         * {@code @Past} rejects today's date as well as future dates, which is
         * correct: a person born today cannot have a valid age for pricing.
         *
         * Expected JSON format: ISO 8601 date string, e.g. {@code "1990-05-15"}.
         */
        @NotNull(message = "dateOfBirth is required")
        @Past(message = "dateOfBirth must be in the past")
        LocalDate dateOfBirth,

        /**
         * Height in centimetres. Required. Bounded to a realistic human range
         * to catch obvious data-entry errors at the boundary.
         *
         * Min 30 cm: smallest plausible height for a young child.
         * Max 300 cm: avoids absurd values while leaving room for error.
         */
        @NotNull(message = "heightCm is required")
        @Min(value = 30, message = "heightCm must be at least 30")
        @Max(value = 300, message = "heightCm must not exceed 300")
        Integer heightCm

) {}
