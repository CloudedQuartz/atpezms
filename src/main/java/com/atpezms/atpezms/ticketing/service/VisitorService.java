package com.atpezms.atpezms.ticketing.service;

import com.atpezms.atpezms.ticketing.dto.CreateVisitorRequest;
import com.atpezms.atpezms.ticketing.dto.VisitorResponse;
import com.atpezms.atpezms.ticketing.entity.Visitor;
import com.atpezms.atpezms.ticketing.repository.VisitorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for visitor registration.
 *
 * <h2>What this service does</h2>
 * Phase 1.1 supports one operation: create a new Visitor record from a
 * validated request DTO. Later slices (Phase 1.3 hardening) may add
 * operations such as update-contact-info or deactivate.
 *
 * <h2>@Transactional on a write method</h2>
 * {@code @Transactional} on {@code createVisitor} ensures the INSERT happens
 * inside a database transaction. If the save throws (e.g. a unique constraint
 * violation from a duplicate email), Spring rolls back the transaction
 * automatically because {@code RuntimeException} is thrown. No partial state
 * is committed.
 *
 * Note: Spring rolls back by default <em>only</em> on unchecked exceptions
 * ({@code RuntimeException} and its subclasses). Our {@code BaseException}
 * hierarchy extends {@code RuntimeException}, so all domain exceptions
 * trigger automatic rollback.
 *
 * <h2>Constructor injection</h2>
 * Dependencies are injected via the constructor rather than field injection
 * ({@code @Autowired} on a field). Constructor injection makes the dependency
 * explicit and the class testable without a Spring context: in a unit test
 * you can simply call {@code new VisitorService(mockRepository)}.
 *
 * <h2>Entities do not leave the service layer</h2>
 * The controller receives a {@link VisitorResponse} record, not a
 * {@link Visitor} entity. This decouples the API contract from the database
 * schema: if the entity changes (e.g. adding an internal status field), the
 * response DTO stays the same unless the API contract intentionally changes.
 */
@Service
public class VisitorService {

    private final VisitorRepository visitorRepository;

    public VisitorService(VisitorRepository visitorRepository) {
        this.visitorRepository = visitorRepository;
    }

    /**
     * Registers a new visitor.
     *
     * <p>The controller has already validated the request shape via
     * {@code @Valid} (blank checks, length limits, past-date constraint).
     * This method applies domain-level validation by constructing the entity
     * via its invariant-enforcing constructor.
     *
     * @param request the validated registration request
     * @return a DTO representing the saved visitor (with decrypted PII)
     */
    @Transactional
    public VisitorResponse createVisitor(CreateVisitorRequest request) {
        // Build the entity. The Visitor constructor re-validates the inputs
        // at the domain level and throws IllegalArgumentException on violation.
        // This is the domain's last line of defence; the @Valid on the
        // controller parameter is the first.
        Visitor visitor = new Visitor(
                request.firstName(),
                request.lastName(),
                request.email(),
                request.phone(),
                request.dateOfBirth(),
                request.heightCm()
        );

        // JPA save: inserts the row and returns the managed entity (with the
        // generated id and auditing timestamps populated). PII fields are
        // encrypted by StringEncryptionConverter during this call.
        Visitor saved = visitorRepository.save(visitor);

        // Map to DTO. At this point PII is already decrypted (Hibernate ran
        // the converter on the way out of the persistence layer).
        return VisitorResponse.from(saved);
    }
}
