package com.atpezms.atpezms.ticketing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atpezms.atpezms.common.exception.BusinessRuleViolationException;
import com.atpezms.atpezms.common.exception.ResourceNotFoundException;
import com.atpezms.atpezms.common.exception.StateConflictException;
import com.atpezms.atpezms.ticketing.dto.IssueVisitRequest;
import com.atpezms.atpezms.ticketing.dto.IssueVisitResponse;
import com.atpezms.atpezms.ticketing.service.VisitService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VisitController.class)
@ActiveProfiles("test")
class VisitControllerTest {
	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private VisitService visitService;

	@Test
	void shouldReturn201WithBodyOnHappyPath() throws Exception {
		when(visitService.issueTicketAndStartVisit(any(IssueVisitRequest.class)))
				.thenReturn(new IssueVisitResponse(
						10L, 20L, 30L,
						250000, "LKR",
						LocalDate.of(2026, 4, 14),
						LocalDate.of(2026, 4, 14)
				));

		mockMvc.perform(post("/api/ticketing/visits")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  \"visitorId\": 1,
								  \"rfidTag\": \"TAG-001\",
								  \"passTypeId\": 2
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.visitId").value(10))
				.andExpect(jsonPath("$.ticketId").value(20))
				.andExpect(jsonPath("$.wristbandId").value(30))
				.andExpect(jsonPath("$.pricePaidCents").value(250000))
				.andExpect(jsonPath("$.currency").value("LKR"));
	}

	@Test
	void shouldReturn400WhenRequestIsInvalid() throws Exception {
		mockMvc.perform(post("/api/ticketing/visits")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  \"visitorId\": 0,
								  \"rfidTag\": \"\",
								  \"passTypeId\": null
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void shouldReturn409WhenServiceThrowsStateConflict() throws Exception {
		when(visitService.issueTicketAndStartVisit(any(IssueVisitRequest.class)))
				.thenThrow(new StateConflictException("WRISTBAND_ALREADY_ACTIVE", "Wristband is already active"));

		mockMvc.perform(post("/api/ticketing/visits")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  \"visitorId\": 1,
								  \"rfidTag\": \"TAG-001\",
								  \"passTypeId\": 2
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("WRISTBAND_ALREADY_ACTIVE"));
	}

	@Test
	void shouldReturn422WhenServiceThrowsBusinessRuleViolation() throws Exception {
		when(visitService.issueTicketAndStartVisit(any(IssueVisitRequest.class)))
				.thenThrow(new BusinessRuleViolationException("CAPACITY_EXCEEDED", "Park has reached maximum daily capacity"));

		mockMvc.perform(post("/api/ticketing/visits")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  \"visitorId\": 1,
								  \"rfidTag\": \"TAG-001\",
								  \"passTypeId\": 2
								}
								"""))
				.andExpect(status().is(422))
				.andExpect(jsonPath("$.code").value("CAPACITY_EXCEEDED"));
	}

	@Test
	void shouldReturn404WhenServiceThrowsNotFound() throws Exception {
		when(visitService.issueTicketAndStartVisit(any(IssueVisitRequest.class)))
				.thenThrow(new ResourceNotFoundException("VISITOR_NOT_FOUND", "Visitor does not exist"));

		mockMvc.perform(post("/api/ticketing/visits")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  \"visitorId\": 999,
								  \"rfidTag\": \"TAG-001\",
								  \"passTypeId\": 2
								}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("VISITOR_NOT_FOUND"));
	}

	@Test
	void shouldReturn400WhenBodyIsMalformedJson() throws Exception {
		mockMvc.perform(post("/api/ticketing/visits")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{ this is not valid json }"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
	}
}
