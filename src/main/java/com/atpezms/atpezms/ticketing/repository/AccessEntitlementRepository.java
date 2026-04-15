package com.atpezms.atpezms.ticketing.repository;

import com.atpezms.atpezms.ticketing.entity.AccessEntitlement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link AccessEntitlement} persistence.
 */
public interface AccessEntitlementRepository extends JpaRepository<AccessEntitlement, Long> {

	/**
	 * Lists all entitlements attached to a ticket.
	 *
	 * <p>Ordering is deterministic to keep debug output and tests stable.
	 */
	@Query("SELECT e FROM AccessEntitlement e "
			+ "WHERE e.ticket.id = :ticketId "
			+ "ORDER BY e.id")
	List<AccessEntitlement> findByTicketId(@Param("ticketId") Long ticketId);
}
