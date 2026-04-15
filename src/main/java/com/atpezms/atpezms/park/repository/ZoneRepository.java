package com.atpezms.atpezms.park.repository;

import com.atpezms.atpezms.park.entity.Zone;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data repository for {@link Zone} persistence.
 *
 * <p>Zones are seeded in Phase 1 so Ticketing can issue zone-based entitlements
 * without waiting for Phase 2 Park CRUD. Phase 2 adds management query methods.
 */
public interface ZoneRepository extends JpaRepository<Zone, Long> {

	/**
	 * Lists all zones ordered by ID (stable, insertion order).
	 *
	 * <p>Used by {@link com.atpezms.atpezms.park.service.ParkReferenceService}
	 * for entitlement issuance (needs a deterministic, stable order).
	 * Management list endpoints use {@link #findAllByOrderByCodeAsc()} instead
	 * (alphabetical, more human-friendly).
	 */
	List<Zone> findAllByOrderByIdAsc();

	/**
	 * Lists zones ordered by code (alphabetical, for management UI).
	 */
	List<Zone> findAllByOrderByCodeAsc();

	/**
	 * Lists only active zones ordered by code.
	 *
	 * <p>Used by {@code GET /api/park/zones?activeOnly=true}.
	 */
	List<Zone> findAllByActiveTrueOrderByCodeAsc();

	/**
	 * Returns true if a zone with the given code already exists.
	 *
	 * <p>Used to enforce code uniqueness with a user-friendly 409 error
	 * before hitting the database UNIQUE constraint.
	 */
	boolean existsByCode(String code);

	/**
	 * Finds a zone by its stable code identifier.
	 *
	 * <p>Useful for human-readable admin lookups (e.g. "get the ADVENTURE zone").
	 */
	Optional<Zone> findByCode(String code);

	/**
	 * Lists zone IDs in a stable order (insertion order).
	 *
	 * <p>Ticket issuance only needs scalar IDs; returning IDs avoids hydrating
	 * full Zone entities on the issuance hot path.
	 */
	@Query("SELECT z.id FROM Zone z ORDER BY z.id")
	List<Long> findAllIdsOrderByIdAsc();
}
