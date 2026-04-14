package com.atpezms.atpezms.ticketing.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atpezms.atpezms.common.exception.ResourceNotFoundException;
import com.atpezms.atpezms.ticketing.dto.RfidActiveVisitResponse;
import com.atpezms.atpezms.ticketing.entity.EntitlementType;
import com.atpezms.atpezms.ticketing.entity.PassTypeCode;
import com.atpezms.atpezms.ticketing.service.RfidResolutionService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RfidResolutionController.class)
@ActiveProfiles("test")
class RfidResolutionControllerTest {
	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RfidResolutionService rfidResolutionService;

	@Test
	void shouldReturn200WithResolvedVisit() throws Exception {
		when(rfidResolutionService.resolveActiveVisitByRfidTag(anyString()))
				.thenReturn(new RfidActiveVisitResponse(
						10L,
						20L,
						30L,
						40L,
						50L,
						PassTypeCode.SINGLE_DAY,
						LocalDate.of(2026, 4, 14),
						LocalDate.of(2026, 4, 14),
						30,
						168,
						List.of(new RfidActiveVisitResponse.EntitlementItem(
								EntitlementType.ZONE,
								1L,
								null,
								null
						))
				));

		mockMvc.perform(get("/api/ticketing/rfid/TAG-001/active-visit"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.visitId").value(10))
				.andExpect(jsonPath("$.visitorId").value(20))
				.andExpect(jsonPath("$.wristbandId").value(30))
				.andExpect(jsonPath("$.passTypeCode").value("SINGLE_DAY"))
				.andExpect(jsonPath("$.entitlements[0].entitlementType").value("ZONE"))
				.andExpect(jsonPath("$.entitlements[0].zoneId").value(1));
	}

	@Test
	void shouldReturn404WhenNoActiveVisit() throws Exception {
		when(rfidResolutionService.resolveActiveVisitByRfidTag(anyString()))
				.thenThrow(new ResourceNotFoundException("ACTIVE_VISIT_NOT_FOUND", "No active visit exists for this RFID tag"));

		mockMvc.perform(get("/api/ticketing/rfid/UNKNOWN/active-visit"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ACTIVE_VISIT_NOT_FOUND"));
	}

	@Test
	void shouldReturn400WhenRfidTagIsBlank() throws Exception {
		// Provide the path variable through a URI template so Spring performs
		// normal encoding/decoding before running @NotBlank.
		mockMvc.perform(get("/api/ticketing/rfid/{rfidTag}/active-visit", " "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}
}
