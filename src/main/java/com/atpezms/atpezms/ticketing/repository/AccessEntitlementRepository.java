package com.atpezms.atpezms.ticketing.repository;

import com.atpezms.atpezms.ticketing.entity.AccessEntitlement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link AccessEntitlement} persistence.
 */
public interface AccessEntitlementRepository extends JpaRepository<AccessEntitlement, Long> {

    /**
     * Lists all entitlements attached to a ticket.
     */
    List<AccessEntitlement> findByTicketId(Long ticketId);
}
