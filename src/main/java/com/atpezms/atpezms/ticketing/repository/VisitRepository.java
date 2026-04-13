package com.atpezms.atpezms.ticketing.repository;

import com.atpezms.atpezms.ticketing.entity.Visit;
import com.atpezms.atpezms.ticketing.entity.VisitStatus;
import com.atpezms.atpezms.ticketing.entity.Wristband;
import com.atpezms.atpezms.ticketing.entity.Visitor;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link Visit} persistence.
 */
public interface VisitRepository extends JpaRepository<Visit, Long> {

    /**
     * Resolves an RFID tag back to its active visit.
     *
     * <p>This powers the PR-1 hot path. The query uses an index-only scan via
     * {@code idx_visits_wristband_status} to quickly locate the single active
     * visit for a given tag. By fetching the associated Visitor and Ticket eagerly
     * here using {@code JOIN FETCH}, we avoid N+1 queries when building the
     * resolution response for other contexts.
     *
     * @param rfidTag the raw tag scanned at the turnstile or POS
     * @return the active visit, fully hydrated with its associations
     */
    @Query("SELECT v FROM Visit v " +
           "JOIN FETCH v.visitor " +
           "JOIN FETCH v.wristband w " +
           "JOIN FETCH v.ticket " +
           "WHERE w.rfidTag = :rfidTag AND v.status = 'ACTIVE'")
    Optional<Visit> findActiveByRfidTag(@Param("rfidTag") String rfidTag);

    /**
     * Checks if a wristband is currently assigned to an active visit.
     */
    boolean existsByWristbandAndStatus(Wristband wristband, VisitStatus status);

    /**
     * Checks if a visitor is currently in the park (already has an active visit).
     */
    boolean existsByVisitorAndStatus(Visitor visitor, VisitStatus status);
}
