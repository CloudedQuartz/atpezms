package com.atpezms.atpezms.park.repository;

import com.atpezms.atpezms.park.entity.Zone;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Park repository for Zone reference data.
 *
 * <p>Zones are seeded in Phase 1 so Ticketing can issue zone-based entitlements
 * without waiting for Phase 2 Park CRUD.
 */
public interface ZoneRepository extends JpaRepository<Zone, Long> {
	/**
	 * Lists all zones in a stable order.
	 *
	 * <p>Ticket issuance creates one entitlement per zone; stable ordering keeps
	 * tests and debugging deterministic.
	 */
	List<Zone> findAllByOrderByIdAsc();
}
