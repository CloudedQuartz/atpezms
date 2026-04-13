package com.atpezms.atpezms.ticketing.dto;

import com.atpezms.atpezms.ticketing.entity.Visitor;
import java.time.LocalDate;

/**
 * Response DTO for a {@link Visitor}.
 *
 * <h2>PII in responses</h2>
 * This endpoint is staff-facing (ticket counter operators). Staff need to
 * confirm they registered the correct visitor, so PII (name, email, phone)
 * is returned. This is documented in {@code PHASE_01_TICKETING_DESIGN.md} §7.2.
 *
 * Rule: PII may appear in HTTP responses to authenticated staff endpoints.
 * It must <em>never</em> appear in application logs.
 *
 * <h2>Why a static factory method?</h2>
 * {@code from(Visitor)} keeps the mapping logic in one place and out of both
 * the controller (which should not know about entity internals) and the entity
 * (which should not depend on DTO classes). The service layer calls this method.
 */
public record VisitorResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        int heightCm
) {
    /**
     * Maps a {@link Visitor} entity to this response DTO.
     *
     * <p>At this point the {@code StringEncryptionConverter} has already
     * decrypted PII fields: Hibernate called the converter on the SELECT
     * that produced the entity, so {@code visitor.getFirstName()} returns
     * plain text, not ciphertext. The controller will serialize this record
     * to JSON via Jackson.
     *
     * @param visitor the saved (or loaded) Visitor entity
     * @return a VisitorResponse containing the visitor's decrypted fields
     */
    public static VisitorResponse from(Visitor visitor) {
        return new VisitorResponse(
                visitor.getId(),
                visitor.getFirstName(),
                visitor.getLastName(),
                visitor.getEmail(),
                visitor.getPhone(),
                visitor.getDateOfBirth(),
                visitor.getHeightCm()
        );
    }
}
