package com.atpezms.atpezms.ticketing.dto;

import com.atpezms.atpezms.ticketing.entity.Ticket;
import com.atpezms.atpezms.ticketing.entity.Visit;
import com.atpezms.atpezms.ticketing.entity.Wristband;
import java.time.LocalDate;

/**
 * Response body for {@code POST /api/ticketing/visits}.
 */
public record IssueVisitResponse(
		Long visitId,
		Long ticketId,
		Long wristbandId,
		int pricePaidCents,
		String currency,
		LocalDate validFrom,
		LocalDate validTo
) {
	public static IssueVisitResponse from(Visit visit) {
		Ticket ticket = visit.getTicket();
		Wristband wristband = visit.getWristband();
		return new IssueVisitResponse(
				visit.getId(),
				ticket.getId(),
				wristband.getId(),
				ticket.getPricePaidCents(),
				ticket.getCurrency(),
				ticket.getValidFrom(),
				ticket.getValidTo()
		);
	}
}
