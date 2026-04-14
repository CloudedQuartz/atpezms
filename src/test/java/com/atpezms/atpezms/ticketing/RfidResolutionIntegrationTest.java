package com.atpezms.atpezms.ticketing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atpezms.atpezms.ticketing.entity.PassTypeCode;
import com.atpezms.atpezms.ticketing.repository.PassTypeRepository;
import com.atpezms.atpezms.ticketing.repository.VisitorRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RfidResolutionIntegrationTest {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private VisitorRepository visitorRepository;

	@Autowired
	private PassTypeRepository passTypeRepository;

	@Autowired
	private Clock clock;

	@Test
	void shouldResolveActiveVisitByRfidAfterIssuance() throws Exception {
		var visitor = visitorRepository.save(new com.atpezms.atpezms.ticketing.entity.Visitor(
				"Asha", "Kumar", null, null,
				LocalDate.of(1995, 7, 10),
				168
		));
		var passType = passTypeRepository.findByCode(PassTypeCode.SINGLE_DAY).orElseThrow();
		LocalDate todayUtc = LocalDate.now(clock.withZone(ZoneOffset.UTC));

		String rfidTag = "RFID-RESOLVE-001";

		mockMvc.perform(post("/api/ticketing/visits")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  \"visitorId\": %d,
							  \"rfidTag\": \"%s\",
							  \"passTypeId\": %d
							}
							""".formatted(visitor.getId(), rfidTag, passType.getId())))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/ticketing/rfid/{rfidTag}/active-visit", rfidTag))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.visitId").isNumber())
				.andExpect(jsonPath("$.visitorId").value(visitor.getId()))
				.andExpect(jsonPath("$.ticketId").isNumber())
				.andExpect(jsonPath("$.wristbandId").isNumber())
				.andExpect(jsonPath("$.passTypeCode").value("SINGLE_DAY"))
				.andExpect(jsonPath("$.validFrom").value(todayUtc.toString()))
				.andExpect(jsonPath("$.validTo").value(todayUtc.toString()))
				.andExpect(jsonPath("$.heightCm").value(168))
				// Phase 1.1: entitlements are not yet created, so the list is empty.
				.andExpect(jsonPath("$.entitlements").isArray());
	}
}
