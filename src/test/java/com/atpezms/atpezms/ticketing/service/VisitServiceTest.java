package com.atpezms.atpezms.ticketing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atpezms.atpezms.common.exception.BusinessRuleViolationException;
import com.atpezms.atpezms.common.exception.StateConflictException;
import com.atpezms.atpezms.park.service.ParkReferenceService;
import com.atpezms.atpezms.ticketing.dto.IssueVisitRequest;
import com.atpezms.atpezms.ticketing.dto.IssueVisitResponse;
import com.atpezms.atpezms.ticketing.entity.EntitlementType;
import com.atpezms.atpezms.ticketing.entity.PassTypeCode;
import com.atpezms.atpezms.ticketing.entity.VisitStatus;
import com.atpezms.atpezms.ticketing.entity.WristbandStatus;
import com.atpezms.atpezms.ticketing.repository.AccessEntitlementRepository;
import com.atpezms.atpezms.ticketing.repository.ParkDayCapacityRepository;
import jakarta.persistence.EntityManager;
import com.atpezms.atpezms.ticketing.repository.PassTypeRepository;
import com.atpezms.atpezms.ticketing.repository.VisitRepository;
import com.atpezms.atpezms.ticketing.repository.VisitorRepository;
import com.atpezms.atpezms.ticketing.repository.WristbandRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VisitServiceTest {
	@Autowired
	private VisitService visitService;

	@Autowired
	private VisitorRepository visitorRepository;

	@Autowired
	private PassTypeRepository passTypeRepository;

	@Autowired
	private WristbandRepository wristbandRepository;

	@Autowired
	private VisitRepository visitRepository;

	@Autowired
	private ParkDayCapacityRepository parkDayCapacityRepository;

	@Autowired
	private AccessEntitlementRepository accessEntitlementRepository;

	@Autowired
	private ParkReferenceService parkReferenceService;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private Clock clock;

	@Test
	void shouldDefaultVisitDateToTodayUtcAndAutoCreateWristband() {
		var visitor = visitorRepository.save(new com.atpezms.atpezms.ticketing.entity.Visitor(
				"Jane", "Doe", null, null,
				LocalDate.of(1990, 1, 1),
				170
		));

		var passType = passTypeRepository.findByCode(PassTypeCode.SINGLE_DAY).orElseThrow();
		LocalDate todayUtc = LocalDate.now(clock.withZone(ZoneOffset.UTC));

		IssueVisitResponse resp = visitService.issueTicketAndStartVisit(new IssueVisitRequest(
				visitor.getId(),
				"NEW-TAG-001",
				passType.getId(),
				null
		));

		assertThat(resp.visitId()).isNotNull();
		assertThat(resp.ticketId()).isNotNull();
		assertThat(resp.wristbandId()).isNotNull();
		assertThat(resp.validFrom()).isEqualTo(todayUtc);
		assertThat(resp.validTo()).isEqualTo(todayUtc);

		var wristband = wristbandRepository.findByRfidTag("NEW-TAG-001").orElseThrow();
		assertThat(wristband.getStatus()).isEqualTo(WristbandStatus.ACTIVE);

		var visit = visitRepository.findById(resp.visitId()).orElseThrow();
		assertThat(visit.getStatus()).isEqualTo(VisitStatus.ACTIVE);
		assertThat(visit.getWristband().getRfidTag()).isEqualTo("NEW-TAG-001");
	}

	@Test
	void shouldRejectVisitDateInPast() {
		var visitor = visitorRepository.save(new com.atpezms.atpezms.ticketing.entity.Visitor(
				"Jane", "Doe", null, null,
				LocalDate.of(1990, 1, 1),
				170
		));
		var passType = passTypeRepository.findByCode(PassTypeCode.SINGLE_DAY).orElseThrow();
		LocalDate yesterdayUtc = LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(1);

		assertThatThrownBy(() -> visitService.issueTicketAndStartVisit(new IssueVisitRequest(
				visitor.getId(),
				"TAG-PAST-001",
				passType.getId(),
				yesterdayUtc
		)))
				.isInstanceOf(BusinessRuleViolationException.class)
				.hasMessageContaining("visitDate must not be in the past");
	}

	@Test
	void shouldRejectWhenWristbandAlreadyActive() {
		var visitor = visitorRepository.save(new com.atpezms.atpezms.ticketing.entity.Visitor(
				"Jane", "Doe", null, null,
				LocalDate.of(1990, 1, 1),
				170
		));
		var passType = passTypeRepository.findByCode(PassTypeCode.SINGLE_DAY).orElseThrow();

		var w = new com.atpezms.atpezms.ticketing.entity.Wristband("ACTIVE-TAG-001");
		w.activate();
		wristbandRepository.saveAndFlush(w);

		assertThatThrownBy(() -> visitService.issueTicketAndStartVisit(new IssueVisitRequest(
				visitor.getId(),
				"ACTIVE-TAG-001",
				passType.getId(),
				null
		)))
				.isInstanceOf(StateConflictException.class)
				.hasMessageContaining("already active");
	}

	@Test
	void shouldRejectWhenVisitorAlreadyHasActiveVisit() {
		var visitor = visitorRepository.save(new com.atpezms.atpezms.ticketing.entity.Visitor(
				"Jane", "Doe", null, null,
				LocalDate.of(1990, 1, 1),
				170
		));
		var passType = passTypeRepository.findByCode(PassTypeCode.SINGLE_DAY).orElseThrow();

		// Create the first ACTIVE visit via the service.
		visitService.issueTicketAndStartVisit(new IssueVisitRequest(visitor.getId(), "TAG-ALREADY-001", passType.getId(), null));

		assertThatThrownBy(() -> visitService.issueTicketAndStartVisit(new IssueVisitRequest(
				visitor.getId(),
				"TAG-ALREADY-002",
				passType.getId(),
				null
		)))
				.isInstanceOf(StateConflictException.class)
				.hasMessageContaining("active visit");
	}

	@Test
	void shouldIssueMultiDayTicketWithCorrectValidityWindow() {
		var visitor = visitorRepository.save(new com.atpezms.atpezms.ticketing.entity.Visitor(
				"Jane", "Doe", null, null,
				LocalDate.of(1990, 1, 1),
				170
		));

		var passType = passTypeRepository.findByCode(PassTypeCode.MULTI_DAY).orElseThrow();
		LocalDate todayUtc = LocalDate.now(clock.withZone(ZoneOffset.UTC));

		IssueVisitResponse resp = visitService.issueTicketAndStartVisit(new IssueVisitRequest(
				visitor.getId(),
				"MULTI-TAG-001",
				passType.getId(),
				todayUtc
		));

		assertThat(resp.validFrom()).isEqualTo(todayUtc);
		assertThat(resp.validTo()).isEqualTo(todayUtc.plusDays(2));

		var visit = visitRepository.findById(resp.visitId()).orElseThrow();
		assertThat(visit.getStatus()).isEqualTo(VisitStatus.ACTIVE);

		var wristband = wristbandRepository.findById(resp.wristbandId()).orElseThrow();
		assertThat(wristband.getStatus()).isEqualTo(WristbandStatus.ACTIVE);
	}

	@Test
	void shouldReserveCapacityForEveryDayInMultiDayWindow() {
		var visitor = visitorRepository.save(new com.atpezms.atpezms.ticketing.entity.Visitor(
				"Jane", "Doe", null, null,
				LocalDate.of(1990, 1, 1),
				170
		));

		var passType = passTypeRepository.findByCode(PassTypeCode.MULTI_DAY).orElseThrow();
		LocalDate todayUtc = LocalDate.now(clock.withZone(ZoneOffset.UTC));

		int before0 = parkDayCapacityRepository.findByVisitDate(todayUtc).map(r -> r.getIssuedCount()).orElse(0);
		int before1 = parkDayCapacityRepository.findByVisitDate(todayUtc.plusDays(1)).map(r -> r.getIssuedCount()).orElse(0);
		int before2 = parkDayCapacityRepository.findByVisitDate(todayUtc.plusDays(2)).map(r -> r.getIssuedCount()).orElse(0);

		visitService.issueTicketAndStartVisit(new IssueVisitRequest(
				visitor.getId(),
				"MULTI-TAG-002",
				passType.getId(),
				todayUtc
		));

		// JPQL bulk updates bypass the first-level cache; clear before reading back.
		entityManager.clear();

		assertThat(parkDayCapacityRepository.findByVisitDate(todayUtc).orElseThrow().getIssuedCount()).isEqualTo(before0 + 1);
		assertThat(parkDayCapacityRepository.findByVisitDate(todayUtc.plusDays(1)).orElseThrow().getIssuedCount()).isEqualTo(before1 + 1);
		assertThat(parkDayCapacityRepository.findByVisitDate(todayUtc.plusDays(2)).orElseThrow().getIssuedCount()).isEqualTo(before2 + 1);
	}

	// Note: all-or-nothing rollback for multi-day capacity is verified in
	// VisitServiceCapacityRollbackTest (non-transactional test that commits setup data).

	@Test
	void shouldRejectInactiveWristband() {
		var visitor = visitorRepository.save(new com.atpezms.atpezms.ticketing.entity.Visitor(
				"Jane", "Doe", null, null,
				LocalDate.of(1990, 1, 1),
				170
		));

		var w = new com.atpezms.atpezms.ticketing.entity.Wristband("INACTIVE-TAG-001");
		w.activate();
		w.makeInactive();
		wristbandRepository.saveAndFlush(w);

		var passType = passTypeRepository.findByCode(PassTypeCode.SINGLE_DAY).orElseThrow();

		assertThatThrownBy(() -> visitService.issueTicketAndStartVisit(new IssueVisitRequest(
				visitor.getId(),
				"INACTIVE-TAG-001",
				passType.getId(),
				null
		)))
				.isInstanceOf(StateConflictException.class)
				.hasMessageContaining("between visit sessions");
	}

	@Test
	void shouldCreateZoneEntitlementsForSingleDayTicket() {
		var visitor = visitorRepository.save(new com.atpezms.atpezms.ticketing.entity.Visitor(
				"Jane", "Doe", null, null,
				LocalDate.of(1990, 1, 1),
				170
		));
		var passType = passTypeRepository.findByCode(PassTypeCode.SINGLE_DAY).orElseThrow();
		int zoneCount = parkReferenceService.listZoneIds().size();

		IssueVisitResponse resp = visitService.issueTicketAndStartVisit(new IssueVisitRequest(
				visitor.getId(),
				"ENT-SINGLE-001",
				passType.getId(),
				null
		));

		var entitlements = accessEntitlementRepository.findByTicketId(resp.ticketId());
		assertThat(entitlements).hasSize(zoneCount);
		assertThat(entitlements)
				.allSatisfy(e -> {
					assertThat(e.getEntitlementType()).isEqualTo(EntitlementType.ZONE);
					assertThat(e.getZoneId()).isNotNull();
					assertThat(e.getRideId()).isNull();
					assertThat(e.getPriorityLevel()).isNull();
				});
		assertThat(entitlements.stream().map(e -> e.getZoneId()).distinct().count()).isEqualTo(zoneCount);
	}

	@Test
	void shouldCreateQueuePriorityEntitlementForFastTrack() {
		var visitor = visitorRepository.save(new com.atpezms.atpezms.ticketing.entity.Visitor(
				"Jane", "Doe", null, null,
				LocalDate.of(1990, 1, 1),
				170
		));
		var passType = passTypeRepository.findByCode(PassTypeCode.FAST_TRACK).orElseThrow();
		int zoneCount = parkReferenceService.listZoneIds().size();

		IssueVisitResponse resp = visitService.issueTicketAndStartVisit(new IssueVisitRequest(
				visitor.getId(),
				"ENT-FAST-001",
				passType.getId(),
				null
		));

		var entitlements = accessEntitlementRepository.findByTicketId(resp.ticketId());
		assertThat(entitlements).hasSize(zoneCount + 1);

		assertThat(entitlements.stream().filter(e -> e.getEntitlementType() == EntitlementType.QUEUE_PRIORITY).toList())
				.singleElement()
				.satisfies(e -> {
					assertThat(e.getPriorityLevel()).isEqualTo(2);
					assertThat(e.getZoneId()).isNull();
					assertThat(e.getRideId()).isNull();
				});
	}

	@Test
	void shouldTreatRideSpecificAsZoneOnlyInPhase1() {
		var visitor = visitorRepository.save(new com.atpezms.atpezms.ticketing.entity.Visitor(
				"Jane", "Doe", null, null,
				LocalDate.of(1990, 1, 1),
				170
		));
		var passType = passTypeRepository.findByCode(PassTypeCode.RIDE_SPECIFIC).orElseThrow();
		int zoneCount = parkReferenceService.listZoneIds().size();

		IssueVisitResponse resp = visitService.issueTicketAndStartVisit(new IssueVisitRequest(
				visitor.getId(),
				"ENT-RIDE-001",
				passType.getId(),
				null
		));

		var entitlements = accessEntitlementRepository.findByTicketId(resp.ticketId());
		assertThat(entitlements).hasSize(zoneCount);
		assertThat(entitlements)
				.allSatisfy(e -> assertThat(e.getEntitlementType()).isEqualTo(EntitlementType.ZONE));
	}

	@Test
	void shouldCreateZoneEntitlementsForMultiDayAndFamilyTickets() {
		int zoneCount = parkReferenceService.listZoneIds().size();

		for (PassTypeCode code : List.of(PassTypeCode.MULTI_DAY, PassTypeCode.FAMILY)) {
			var visitor = visitorRepository.save(new com.atpezms.atpezms.ticketing.entity.Visitor(
					"Jane", "Doe", null, null,
					LocalDate.of(1990, 1, 1),
					170
			));
			var passType = passTypeRepository.findByCode(code).orElseThrow();
			IssueVisitResponse resp = visitService.issueTicketAndStartVisit(new IssueVisitRequest(
					visitor.getId(),
					"ENT-" + code + "-001",
					passType.getId(),
					null
			));

			var entitlements = accessEntitlementRepository.findByTicketId(resp.ticketId());
			assertThat(entitlements).hasSize(zoneCount);
			assertThat(entitlements)
					.allSatisfy(e -> assertThat(e.getEntitlementType()).isEqualTo(EntitlementType.ZONE));
		}
	}
}
