package com.atpezms.atpezms.ticketing.repository;

import com.atpezms.atpezms.ticketing.entity.ParkDayCapacity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for per-day capacity counters.
 */
public interface ParkDayCapacityRepository extends JpaRepository<ParkDayCapacity, Long> {

    Optional<ParkDayCapacity> findByVisitDate(LocalDate visitDate);

    /**
     * Atomically increments issued_count if capacity is still available.
     *
     * <p>Returns number of affected rows:
     * <ul>
     *   <li>1 = success (slot reserved)</li>
     *   <li>0 = either sold out (issued_count already == max_capacity) or no row exists for the date</li>
     * </ul>
     *
     * <p>Important: This is a JPQL bulk update. It bypasses the persistence context and entity
     * lifecycle callbacks (including JPA auditing). Do not rely on {@code @PreUpdate} validation
     * or auditing for this operation.
     *
     * <p>Transactional rule: capacity reservation must be part of the overall ticket issuance
     * transaction (so we do not consume capacity if later steps fail). This method therefore
     * requires an existing transaction.
     *
     * <p>Persistence-context rule: if a {@link ParkDayCapacity} entity for the same date is
     * already managed in the current persistence context, its {@code issuedCount} will be stale
     * after this bulk update. Callers must {@code clear()} the persistence context or
     * {@code refresh(...)} the entity before reading the counter.
     *
     * @param now timestamp to write to {@code updatedAt}. Callers should pass {@code Instant.now()}
     *            at the service boundary; do not pass null.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query("""
            UPDATE ParkDayCapacity p
            SET p.issuedCount = p.issuedCount + 1,
                p.updatedAt = :now
            WHERE p.visitDate = :visitDate
              AND p.issuedCount < p.maxCapacity
            """)
    int incrementIfCapacityAvailable(
            @Param("visitDate") LocalDate visitDate,
            @Param("now") Instant now);
}
