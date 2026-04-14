package com.atpezms.atpezms.ticketing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atpezms.atpezms.common.exception.BusinessRuleViolationException;
import com.atpezms.atpezms.common.exception.StateConflictException;
import com.atpezms.atpezms.ticketing.dto.IssueVisitRequest;
import com.atpezms.atpezms.ticketing.dto.IssueVisitResponse;
import com.atpezms.atpezms.ticketing.entity.PassTypeCode;
import com.atpezms.atpezms.ticketing.entity.VisitStatus;
import com.atpezms.atpezms.ticketing.entity.WristbandStatus;
import com.atpezms.atpezms.ticketing.repository.PassTypeRepository;
import com.atpezms.atpezms.ticketing.repository.VisitRepository;
import com.atpezms.atpezms.ticketing.repository.VisitorRepository;
import com.atpezms.atpezms.ticketing.repository.WristbandRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
}
