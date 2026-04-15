package com.atpezms.atpezms.park.repository;

import com.atpezms.atpezms.park.entity.ParkConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link ParkConfiguration} persistence.
 *
 * <p>Phase 1 uses this to read the active configuration for capacity enforcement.
 * Phase 2 adds management methods for the activate-on-create flow.
 */
public interface ParkConfigurationRepository extends JpaRepository<ParkConfiguration, Long> {

    /**
     * Finds the currently active park configuration.
     *
     * <p>Business rule: exactly one row may have {@code active = true} at a time.
     * This invariant is maintained by the service layer via the
     * {@code park_write_lock} pessimistic lock.
     */
    Optional<ParkConfiguration> findByActiveTrue();

    /**
     * Returns all configurations ordered by ID descending (newest first).
     *
     * <p>Used by {@code GET /api/park/configurations} to show the full
     * history with the current active config at the top.
     */
    List<ParkConfiguration> findAllByOrderByIdDesc();

    /**
     * Deactivates all currently active configurations.
     *
     * <p>Called atomically before creating a new active configuration.
     * Returns the number of rows updated (expected: 1 in a healthy system;
     * 0 means no active config existed, which is an invariant violation).
     *
     * <h2>JPQL bulk update caveat</h2>
     * This bypasses the JPA persistence context and entity lifecycle callbacks
     * (including {@code @LastModifiedDate}). We therefore set {@code updatedAt}
     * explicitly in the query ({@code IMPLEMENTATION.md §6.1}).
     *
     * <h2>Must be called inside an existing transaction</h2>
     * This method must always be called from within the activate-on-create
     * service transaction so the deactivation and the new row creation are atomic.
     */
    @Modifying
    @Query("UPDATE ParkConfiguration c SET c.active = false, c.updatedAt = :now WHERE c.active = true")
    int deactivateAll(@Param("now") Instant now);
}
