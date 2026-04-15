package com.atpezms.atpezms.ticketing.service;

import com.atpezms.atpezms.common.entity.SeasonType;
import com.atpezms.atpezms.common.exception.BusinessRuleViolationException;
import com.atpezms.atpezms.common.exception.ResourceNotFoundException;
import com.atpezms.atpezms.common.exception.StateConflictException;
import com.atpezms.atpezms.park.service.ParkReferenceService;
import com.atpezms.atpezms.ticketing.dto.IssueVisitRequest;
import com.atpezms.atpezms.ticketing.dto.IssueVisitResponse;
import com.atpezms.atpezms.ticketing.entity.AccessEntitlement;
import com.atpezms.atpezms.ticketing.entity.AgeGroup;
import com.atpezms.atpezms.ticketing.entity.DayType;
import com.atpezms.atpezms.ticketing.entity.EntitlementType;
import com.atpezms.atpezms.ticketing.entity.ParkDayCapacity;
import com.atpezms.atpezms.ticketing.entity.PassType;
import com.atpezms.atpezms.ticketing.entity.PassTypeCode;
import com.atpezms.atpezms.ticketing.entity.PassTypePrice;
import com.atpezms.atpezms.ticketing.entity.Ticket;
import com.atpezms.atpezms.ticketing.entity.Visit;
import com.atpezms.atpezms.ticketing.entity.VisitStatus;
import com.atpezms.atpezms.ticketing.entity.Visitor;
import com.atpezms.atpezms.ticketing.entity.Wristband;
import com.atpezms.atpezms.ticketing.entity.WristbandStatus;
import com.atpezms.atpezms.ticketing.repository.AccessEntitlementRepository;
import com.atpezms.atpezms.ticketing.repository.ParkDayCapacityRepository;
import com.atpezms.atpezms.ticketing.repository.PassTypePriceRepository;
import com.atpezms.atpezms.ticketing.repository.PassTypeRepository;
import com.atpezms.atpezms.ticketing.repository.TicketRepository;
import com.atpezms.atpezms.ticketing.repository.VisitRepository;
import com.atpezms.atpezms.ticketing.repository.VisitorRepository;
import com.atpezms.atpezms.ticketing.repository.WristbandRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Core Ticketing use case: sell a ticket, associate a wristband, and start an ACTIVE visit.
 *
 * <p>Phase 1.1: single-day issuance (SINGLE_DAY, RIDE_SPECIFIC, FAMILY, FAST_TRACK pass types).
 * <p>Phase 1.2: multi-day issuance (MULTI_DAY pass type). Validity window is fixed at purchase:
 * {@code validFrom = visitDate}, {@code validTo = visitDate + multiDayCount - 1}. Capacity is
 * reserved atomically for every date in {@code [validFrom, validTo]}; if any single day is sold
 * out the whole transaction rolls back.
 */
@Service
public class VisitService {
	private static final int FAST_TRACK_PRIORITY_LEVEL = 2;

	private final VisitorRepository visitorRepository;
	private final PassTypeRepository passTypeRepository;
	private final PassTypePriceRepository passTypePriceRepository;
	private final WristbandRepository wristbandRepository;
	private final TicketRepository ticketRepository;
	private final VisitRepository visitRepository;
	private final AccessEntitlementRepository accessEntitlementRepository;
	private final ParkDayCapacityRepository parkDayCapacityRepository;
	private final ParkReferenceService parkReferenceService;
	private final Clock clock;
	private final TransactionTemplate requiresNewTx;

	public VisitService(
			VisitorRepository visitorRepository,
			PassTypeRepository passTypeRepository,
			PassTypePriceRepository passTypePriceRepository,
			WristbandRepository wristbandRepository,
			TicketRepository ticketRepository,
			VisitRepository visitRepository,
			AccessEntitlementRepository accessEntitlementRepository,
			ParkDayCapacityRepository parkDayCapacityRepository,
			ParkReferenceService parkReferenceService,
			Clock clock,
			PlatformTransactionManager transactionManager
	) {
		this.visitorRepository = visitorRepository;
		this.passTypeRepository = passTypeRepository;
		this.passTypePriceRepository = passTypePriceRepository;
		this.wristbandRepository = wristbandRepository;
		this.ticketRepository = ticketRepository;
		this.visitRepository = visitRepository;
		this.accessEntitlementRepository = accessEntitlementRepository;
		this.parkDayCapacityRepository = parkDayCapacityRepository;
		this.parkReferenceService = parkReferenceService;
		this.clock = clock;
		this.requiresNewTx = new TransactionTemplate(transactionManager);
		this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Transactional
	public IssueVisitResponse issueTicketAndStartVisit(IssueVisitRequest request) {
		LocalDate todayUtc = LocalDate.now(clock.withZone(ZoneOffset.UTC));
		LocalDate visitDate = request.visitDate() != null ? request.visitDate() : todayUtc;
		if (visitDate.isBefore(todayUtc)) {
			throw new BusinessRuleViolationException("VISIT_DATE_IN_PAST", "visitDate must not be in the past");
		}

		// Lock the visitor row to serialize concurrent issuance attempts for the same visitor.
		Visitor visitor = visitorRepository.findByIdForUpdate(request.visitorId())
				.orElseThrow(() -> new ResourceNotFoundException("VISITOR_NOT_FOUND", "Visitor does not exist"));

		PassType passType = passTypeRepository.findById(request.passTypeId())
				.orElseThrow(() -> new ResourceNotFoundException("PASS_TYPE_NOT_FOUND", "Pass type does not exist"));
		if (!passType.isActive()) {
			throw new BusinessRuleViolationException("PASS_TYPE_INACTIVE", "Pass type is inactive");
		}

		if (visitRepository.existsByVisitorAndStatus(visitor, VisitStatus.ACTIVE)) {
			throw new StateConflictException("VISITOR_ALREADY_IN_PARK", "Visitor already has an active visit");
		}

		Wristband wristband = findOrCreateWristbandForUpdateOrThrow(request.rfidTag());
		if (wristband.getStatus() == WristbandStatus.ACTIVE) {
			throw new StateConflictException("WRISTBAND_ALREADY_ACTIVE", "Wristband is already active");
		}
		if (wristband.getStatus() == WristbandStatus.INACTIVE) {
			// INACTIVE means the wristband was previously issued and is physically on a visitor's
			// wrist between visit sessions (e.g. overnight for a multi-day pass). It cannot be
			// re-issued to anyone via this endpoint. Day-N re-entry for the same visitor uses a
			// separate flow implemented in Phase 4 (after checkout / Visit-end exists).
			throw new StateConflictException("WRISTBAND_INACTIVE",
					"Wristband is between visit sessions; use the day-N re-entry flow");
		}
		if (wristband.getStatus() == WristbandStatus.DEACTIVATED) {
			throw new StateConflictException("WRISTBAND_DEACTIVATED", "Wristband is deactivated");
		}
		if (wristband.getStatus() != WristbandStatus.IN_STOCK) {
			throw new StateConflictException("WRISTBAND_NOT_AVAILABLE", "Wristband is not available for activation");
		}

		// Compute validity window.
		// For single-day pass types the window is one day: validFrom == validTo == visitDate.
		// For MULTI_DAY the window spans multiDayCount consecutive days starting from visitDate.
		// visitDate (= validFrom) is fixed at purchase time and is immutable.
		LocalDate validFrom = visitDate;
		LocalDate validTo;
		if (passType.getCode() == PassTypeCode.MULTI_DAY) {
			Integer multiDayCount = passType.getMultiDayCount();
			if (multiDayCount == null || multiDayCount <= 0) {
				// Defensive guard: the DB CHECK and entity validation should prevent this,
				// but a corrupted or hand-edited row must not cause a silent NPE.
				throw new BusinessRuleViolationException("PASS_TYPE_MISCONFIGURED",
						"MULTI_DAY pass type has an invalid multiDayCount: " + multiDayCount);
			}
			validTo = visitDate.plusDays(multiDayCount - 1);
		} else {
			validTo = visitDate;
		}

		// Reserve capacity for every date in [validFrom, validTo] inside this transaction.
		// All-or-nothing: if any single day is sold out the loop throws and the transaction
		// rolls back, releasing any capacity that was incremented for earlier dates in the loop.
		for (LocalDate d = validFrom; !d.isAfter(validTo); d = d.plusDays(1)) {
			reserveCapacityOrThrow(d);
		}

		// Price lookup uses visitDate (= validFrom) attributes.
		// For MULTI_DAY the pass_type_prices row holds the total price for the whole pass,
		// not a per-day rate. Anchoring the lookup to the start date is intentional.
		AgeGroup ageGroup = toAgeGroup(visitor.getDateOfBirth(), visitDate);
		DayType dayType = toDayType(visitDate);
		SeasonType seasonType = parkReferenceService.getSeasonTypeForDate(visitDate);
		PassTypePrice price = passTypePriceRepository
				.findByPassTypeAndAgeGroupAndDayTypeAndSeasonType(passType, ageGroup, dayType, seasonType)
				.orElseThrow(() -> new BusinessRuleViolationException(
						"PRICE_NOT_CONFIGURED",
						"No price configured for pass type and visit date"
				));

		Instant now = Instant.now(clock);

		Ticket ticket = ticketRepository.save(new Ticket(
				visitor,
				passType,
				visitDate,
				validFrom,
				validTo,
				price.getPriceCents(),
				price.getCurrency(),
				now
		));

		// Phase 1.3: persist the ticket's entitlement snapshot at issuance time.
		createEntitlementsOrThrow(ticket);

		wristband.activate();
		wristbandRepository.save(wristband);

		Visit visit = visitRepository.save(new Visit(visitor, wristband, ticket, now));
		return IssueVisitResponse.from(visit);
	}

	private void createEntitlementsOrThrow(Ticket ticket) {
		PassTypeCode code = ticket.getPassType().getCode();
		List<AccessEntitlement> entitlements = new ArrayList<>();

		// In Phase 1, "full park access" is represented as one ZONE entitlement row
		// per seeded zone. Phase 1 also treats RIDE_SPECIFIC as zone-only until the
		// Rides context owns ride reference data.
		switch (code) {
			case SINGLE_DAY, MULTI_DAY, FAMILY, FAST_TRACK, RIDE_SPECIFIC -> {
				var zoneIds = parkReferenceService.listZoneIds();
				if (zoneIds.isEmpty()) {
					throw new IllegalStateException("No park zones are configured; cannot issue zone entitlements");
				}
				zoneIds.forEach(zoneId ->
						entitlements.add(new AccessEntitlement(ticket, EntitlementType.ZONE, zoneId, null, null))
				);
			}
			default -> throw new IllegalStateException("Unsupported pass type code: " + code);
		}

		if (code == PassTypeCode.FAST_TRACK) {
			entitlements.add(new AccessEntitlement(ticket, EntitlementType.QUEUE_PRIORITY, null, null, FAST_TRACK_PRIORITY_LEVEL));
		}

		accessEntitlementRepository.saveAll(entitlements);
	}

	private Wristband findOrCreateWristbandForUpdateOrThrow(String rfidTag) {
		return wristbandRepository.findByRfidTagForUpdate(rfidTag)
				.orElseGet(() -> {
					try {
						// We flush so we find out about unique-key conflicts while we're still
						// inside the service method and can map the error to a deterministic 409.
						return wristbandRepository.saveAndFlush(new Wristband(rfidTag));
					} catch (DataIntegrityViolationException e) {
						// Most likely: two concurrent requests tried to auto-register the same new tag.
						// We do NOT attempt to recover and continue in this transaction. After a
						// persistence exception on flush, JPA providers can mark the persistence
						// context/transaction as unsafe to reuse.
						throw new StateConflictException(
								"WRISTBAND_RFID_TAG_CONFLICT",
								"RFID tag was registered concurrently; retry the request"
						);
					}
				});
	}

	private void reserveCapacityOrThrow(LocalDate visitDate) {
		Instant now = Instant.now(clock);
		int updated = parkDayCapacityRepository.incrementIfCapacityAvailable(visitDate, now);
		if (updated == 1) {
			return;
		}

		// Retry once if the day row was missing.
		//
		// We create the row in a separate transaction so a unique-constraint race
		// (two issuances both trying to create the day row) cannot poison the main
		// ticket issuance transaction.
		createCapacityRowIfMissing(visitDate);
		updated = parkDayCapacityRepository.incrementIfCapacityAvailable(visitDate, now);
		if (updated == 1) {
			return;
		}

		throw new BusinessRuleViolationException("CAPACITY_EXCEEDED", "Park has reached maximum daily capacity");
	}

	private void createCapacityRowIfMissing(LocalDate visitDate) {
		requiresNewTx.executeWithoutResult(status -> {
			if (parkDayCapacityRepository.findByVisitDate(visitDate).isPresent()) {
				return;
			}

			int maxCapacity = parkReferenceService.getActiveMaxDailyCapacity();
			try {
				parkDayCapacityRepository.saveAndFlush(new ParkDayCapacity(visitDate, maxCapacity));
			} catch (DataIntegrityViolationException e) {
				// If this was the expected unique-key race, the row should now exist.
				// If it still doesn't exist, rethrow because something else is wrong.
				if (parkDayCapacityRepository.findByVisitDate(visitDate).isEmpty()) {
					throw e;
				}
			}
		});
	}

	private static DayType toDayType(LocalDate date) {
		DayOfWeek d = date.getDayOfWeek();
		return (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY) ? DayType.WEEKEND : DayType.WEEKDAY;
	}

	private static AgeGroup toAgeGroup(LocalDate dateOfBirth, LocalDate visitDate) {
		int years = visitDate.getYear() - dateOfBirth.getYear();
		if (visitDate.isBefore(dateOfBirth.plusYears(years))) {
			years -= 1;
		}
		if (years < 12) {
			return AgeGroup.CHILD;
		}
		if (years >= 60) {
			return AgeGroup.SENIOR;
		}
		return AgeGroup.ADULT;
	}
}
