package com.atpezms.atpezms.ticketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.atpezms.atpezms.ticketing.entity.PassTypeCode;
import com.atpezms.atpezms.ticketing.repository.ParkDayCapacityRepository;
import com.atpezms.atpezms.ticketing.repository.PassTypeRepository;
import com.atpezms.atpezms.ticketing.repository.TicketRepository;
import com.atpezms.atpezms.ticketing.repository.VisitRepository;
import com.atpezms.atpezms.ticketing.repository.VisitorRepository;
import com.atpezms.atpezms.ticketing.repository.WristbandRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class VisitIntegrationTest {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private VisitorRepository visitorRepository;

	@Autowired
	private PassTypeRepository passTypeRepository;

	@Autowired
	private WristbandRepository wristbandRepository;

	@Autowired
	private TicketRepository ticketRepository;

	@Autowired
	private VisitRepository visitRepository;

	@Autowired
	private ParkDayCapacityRepository parkDayCapacityRepository;

	@Autowired
	private Clock clock;

	@Test
	void shouldIssueTicketActivateWristbandAndStartVisit() throws Exception {
		var visitor = visitorRepository.save(new com.atpezms.atpezms.ticketing.entity.Visitor(
				"Asha", "Kumar", null, null,
				LocalDate.of(1995, 7, 10),
				168
		));
		var passType = passTypeRepository.findByCode(PassTypeCode.SINGLE_DAY).orElseThrow();
		LocalDate todayUtc = LocalDate.now(clock.withZone(ZoneOffset.UTC));

		String responseBody = mockMvc.perform(post("/api/ticketing/visits")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  \"visitorId\": %d,
								  \"rfidTag\": \"INTEG-TAG-001\",
								  \"passTypeId\": %d
								}
								""".formatted(visitor.getId(), passType.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.visitId").isNumber())
				.andExpect(jsonPath("$.ticketId").isNumber())
				.andExpect(jsonPath("$.wristbandId").isNumber())
				.andExpect(jsonPath("$.validFrom").value(todayUtc.toString()))
				.andExpect(jsonPath("$.validTo").value(todayUtc.toString()))
				.andReturn()
				.getResponse()
				.getContentAsString();

		var json = JsonPath.parse(responseBody);
		long visitId = ((Number) json.read("$.visitId")).longValue();
		long ticketId = ((Number) json.read("$.ticketId")).longValue();
		long wristbandId = ((Number) json.read("$.wristbandId")).longValue();

		var wristband = wristbandRepository.findById(wristbandId).orElseThrow();
		assertThat(wristband.getRfidTag()).isEqualTo("INTEG-TAG-001");
		assertThat(wristband.getStatus().name()).isEqualTo("ACTIVE");

		var ticket = ticketRepository.findById(ticketId).orElseThrow();
		assertThat(ticket.getVisitDate()).isEqualTo(todayUtc);
		assertThat(ticket.getValidFrom()).isEqualTo(todayUtc);
		assertThat(ticket.getValidTo()).isEqualTo(todayUtc);

		var visit = visitRepository.findById(visitId).orElseThrow();
		assertThat(visit.getVisitor().getId()).isEqualTo(visitor.getId());
		assertThat(visit.getWristband().getId()).isEqualTo(wristbandId);
		assertThat(visit.getTicket().getId()).isEqualTo(ticketId);
		assertThat(visit.getStatus().name()).isEqualTo("ACTIVE");
	}

	@Test
	void shouldIssueMultiDayTicketAndReserveCapacityForAllDays() throws Exception {
		var visitor = visitorRepository.save(new com.atpezms.atpezms.ticketing.entity.Visitor(
				"Asha", "Kumar", null, null,
				LocalDate.of(1995, 7, 10),
				168
		));
		var passType = passTypeRepository.findByCode(PassTypeCode.MULTI_DAY).orElseThrow();
		LocalDate todayUtc = LocalDate.now(clock.withZone(ZoneOffset.UTC));

		String responseBody = mockMvc.perform(post("/api/ticketing/visits")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  \"visitorId\": %d,
								  \"rfidTag\": \"INTEG-MULTI-001\",
								  \"passTypeId\": %d
								}
								""".formatted(visitor.getId(), passType.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.validFrom").value(todayUtc.toString()))
				.andExpect(jsonPath("$.validTo").value(todayUtc.plusDays(2).toString()))
				.andReturn()
				.getResponse()
				.getContentAsString();

		var json = JsonPath.parse(responseBody);
		long ticketId = ((Number) json.read("$.ticketId")).longValue();
		var ticket = ticketRepository.findById(ticketId).orElseThrow();
		assertThat(ticket.getValidFrom()).isEqualTo(todayUtc);
		assertThat(ticket.getValidTo()).isEqualTo(todayUtc.plusDays(2));

		assertThat(parkDayCapacityRepository.findByVisitDate(todayUtc)).isPresent();
		assertThat(parkDayCapacityRepository.findByVisitDate(todayUtc.plusDays(1))).isPresent();
		assertThat(parkDayCapacityRepository.findByVisitDate(todayUtc.plusDays(2))).isPresent();

		parkDayCapacityRepository.findByVisitDate(todayUtc).ifPresent(cap ->
				assertThat(cap.getIssuedCount()).isGreaterThanOrEqualTo(1));
		parkDayCapacityRepository.findByVisitDate(todayUtc.plusDays(1)).ifPresent(cap ->
				assertThat(cap.getIssuedCount()).isGreaterThanOrEqualTo(1));
		parkDayCapacityRepository.findByVisitDate(todayUtc.plusDays(2)).ifPresent(cap ->
				assertThat(cap.getIssuedCount()).isGreaterThanOrEqualTo(1));
	}
}
