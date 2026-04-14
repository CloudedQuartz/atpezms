package com.atpezms.atpezms.ticketing.dto;

import com.atpezms.atpezms.ticketing.entity.EntitlementType;
import com.atpezms.atpezms.ticketing.entity.PassTypeCode;
import com.atpezms.atpezms.ticketing.entity.Visitor;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * Response body for the Phase 1.1 debug RFID resolution endpoint:
 * {@code GET /api/ticketing/rfid/{rfidTag}/active-visit}.
 *
 * <p>This DTO is intentionally shaped as "scan hot path" data: the minimal
 * attributes other contexts need to make allow/deny decisions.
 */
public record RfidActiveVisitResponse(
		Long visitId,
		Long visitorId,
		Long wristbandId,
		Long ticketId,
		Long passTypeId,
		PassTypeCode passTypeCode,
		LocalDate validFrom,
		LocalDate validTo,
		int ageYears,
		int heightCm,
		List<EntitlementItem> entitlements
) {
	public record EntitlementItem(
			EntitlementType entitlementType,
			Long zoneId,
			Long rideId,
			Integer priorityLevel
	) {}

	public static int computeAgeYears(Visitor visitor, LocalDate onDate) {
		// Age is computed from DOB and the visit's anchor date.
		// This keeps scan-time eligibility logic consistent across contexts.
		return Period.between(visitor.getDateOfBirth(), onDate).getYears();
	}
}
