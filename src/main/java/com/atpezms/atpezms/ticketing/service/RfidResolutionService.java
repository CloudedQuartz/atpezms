package com.atpezms.atpezms.ticketing.service;

import com.atpezms.atpezms.common.exception.ResourceNotFoundException;
import com.atpezms.atpezms.ticketing.dto.RfidActiveVisitResponse;
import com.atpezms.atpezms.ticketing.entity.AccessEntitlement;
import com.atpezms.atpezms.ticketing.entity.Visit;
import com.atpezms.atpezms.ticketing.repository.AccessEntitlementRepository;
import com.atpezms.atpezms.ticketing.repository.VisitRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves an RFID tag to the current ACTIVE visit.
 *
 * <p>Why this service exists (DESIGN.md §3.5): Ticketing is the single system of
 * record for mapping "RFID tag -> active Visit". Other scan-processing contexts
 * (Rides, Food, Merchandise, Events) depend on this rather than doing their own
 * lookups.
 */
@Service
public class RfidResolutionService {
	private final VisitRepository visitRepository;
	private final AccessEntitlementRepository accessEntitlementRepository;

	public RfidResolutionService(
			VisitRepository visitRepository,
			AccessEntitlementRepository accessEntitlementRepository
	) {
		this.visitRepository = visitRepository;
		this.accessEntitlementRepository = accessEntitlementRepository;
	}

	@Transactional(readOnly = true)
	public RfidActiveVisitResponse resolveActiveVisitByRfidTag(String rfidTag) {
		Visit visit = visitRepository.findActiveByRfidTag(rfidTag)
				.orElseThrow(() -> new ResourceNotFoundException(
						"ACTIVE_VISIT_NOT_FOUND",
						"No active visit exists for this RFID tag"
				));

		var ticket = visit.getTicket();
		var passType = ticket.getPassType();
		var visitor = visit.getVisitor();

		// Age/eligibility is anchored to the visit date (the ticket validity anchor),
		// not the current wall-clock day.
		LocalDate anchorDate = ticket.getVisitDate();
		int ageYears = RfidActiveVisitResponse.computeAgeYears(visitor, anchorDate);

		List<RfidActiveVisitResponse.EntitlementItem> entitlements = accessEntitlementRepository
				.findByTicketId(ticket.getId())
				.stream()
				.map(RfidResolutionService::toEntitlementItem)
				.toList();

		return new RfidActiveVisitResponse(
				visit.getId(),
				visitor.getId(),
				visit.getWristband().getId(),
				ticket.getId(),
				passType.getId(),
				passType.getCode(),
				ticket.getValidFrom(),
				ticket.getValidTo(),
				ageYears,
				visitor.getHeightCm(),
				entitlements
		);
	}

	private static RfidActiveVisitResponse.EntitlementItem toEntitlementItem(AccessEntitlement e) {
		return new RfidActiveVisitResponse.EntitlementItem(
				e.getEntitlementType(),
				e.getZoneId(),
				e.getRideId(),
				e.getPriorityLevel()
		);
	}
}
