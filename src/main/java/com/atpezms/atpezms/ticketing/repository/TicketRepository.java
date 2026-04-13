package com.atpezms.atpezms.ticketing.repository;

import com.atpezms.atpezms.ticketing.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Ticket} persistence.
 */
public interface TicketRepository extends JpaRepository<Ticket, Long> {
}
