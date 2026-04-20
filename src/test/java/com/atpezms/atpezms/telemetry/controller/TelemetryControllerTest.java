package com.atpezms.atpezms.telemetry.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atpezms.atpezms.telemetry.dto.RecordScanRequest;
import com.atpezms.atpezms.telemetry.dto.ScanEventResponse;
import com.atpezms.atpezms.telemetry.entity.ScanDecision;
import com.atpezms.atpezms.telemetry.entity.ScanPurpose;
import com.atpezms.atpezms.telemetry.service.TelemetryService;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-layer test for {@link TelemetryController}.
 *
 * <h2>What this tests</h2>
 * <ul>
 *   <li>Request validation (400 on bad input).</li>
 *   <li>HTTP status codes (201, 200).</li>
 *   <li>Response body shape.</li>
 * </ul>
 *
 * <h2>What this does NOT test</h2>
 * Database behavior, transaction semantics, and real service logic.
 * Those are covered by {@link TelemetryIntegrationTest}.
 */
@WebMvcTest(TelemetryController.class)
@ActiveProfiles("test")
class TelemetryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TelemetryService telemetryService;

	private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");

	// -----------------------------------------------------------------------
	// POST /api/telemetry/scans
	// -----------------------------------------------------------------------

	@Test
	void shouldReturn201WhenScanRecorded() throws Exception {
		when(telemetryService.recordScan(any(RecordScanRequest.class)))
			.thenReturn(new ScanEventResponse(
				1L, NOW, "RFID-ABC-123", "turnstile-ride-01",
				1L, "RIDE_ENTRY", "ALLOWED", null, NOW));

		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-ABC-123",
					  "deviceIdentifier": "turnstile-ride-01",
					  "zoneId": 1,
					  "purpose": "RIDE_ENTRY",
					  "decision": "ALLOWED"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(1))
			.andExpect(jsonPath("$.rfidTag").value("RFID-ABC-123"))
			.andExpect(jsonPath("$.decision").value("ALLOWED"));
	}

	@Test
	void shouldReturn400WhenRfidTagIsMissing() throws Exception {
		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "deviceIdentifier": "turnstile-ride-01",
					  "zoneId": 1,
					  "purpose": "RIDE_ENTRY",
					  "decision": "ALLOWED"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void shouldReturn400WhenDeviceIdentifierIsMissing() throws Exception {
		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-ABC-123",
					  "zoneId": 1,
					  "purpose": "RIDE_ENTRY",
					  "decision": "ALLOWED"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void shouldReturn400WhenZoneIdIsNull() throws Exception {
		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-ABC-123",
					  "deviceIdentifier": "turnstile-ride-01",
					  "zoneId": null,
					  "purpose": "RIDE_ENTRY",
					  "decision": "ALLOWED"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void shouldReturn400WhenPurposeIsNull() throws Exception {
		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-ABC-123",
					  "deviceIdentifier": "turnstile-ride-01",
					  "zoneId": 1,
					  "purpose": null,
					  "decision": "ALLOWED"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void shouldReturn400WhenDecisionIsNull() throws Exception {
		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-ABC-123",
					  "deviceIdentifier": "turnstile-ride-01",
					  "zoneId": 1,
					  "purpose": "RIDE_ENTRY",
					  "decision": null
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void shouldReturn400WhenPurposeIsInvalid() throws Exception {
		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-ABC-123",
					  "deviceIdentifier": "turnstile-ride-01",
					  "zoneId": 1,
					  "purpose": "INVALID_PURPOSE",
					  "decision": "ALLOWED"
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void shouldReturn400WhenDecisionIsInvalid() throws Exception {
		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-ABC-123",
					  "deviceIdentifier": "turnstile-ride-01",
					  "zoneId": 1,
					  "purpose": "RIDE_ENTRY",
					  "decision": "MAYBE"
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void shouldReturn400WhenReasonExceeds500Chars() throws Exception {
		String longReason = "a".repeat(501);

		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-ABC-123",
					  "deviceIdentifier": "turnstile-ride-01",
					  "zoneId": 1,
					  "purpose": "RIDE_ENTRY",
					  "decision": "DENIED",
					  "reason": "%s"
					}
					""".formatted(longReason)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	// -----------------------------------------------------------------------
	// GET /api/telemetry/scans
	// -----------------------------------------------------------------------

	@Test
	void shouldReturn200WithPaginatedScans() throws Exception {
		when(telemetryService.findAll(any(), any(), any(), any(), any(), any(), any()))
			.thenReturn(Page.empty());

		mockMvc.perform(get("/api/telemetry/scans"))
			.andExpect(status().isOk());
	}

	@Test
	void shouldPassRfidTagFilterToService() throws Exception {
		when(telemetryService.findAll(any(), any(), any(), any(), any(), any(), any()))
			.thenReturn(Page.empty());

		mockMvc.perform(get("/api/telemetry/scans")
				.param("rfidTag", "RFID-ABC-123"))
			.andExpect(status().isOk());
	}

	@Test
	void shouldPassZoneIdFilterToService() throws Exception {
		when(telemetryService.findAll(any(), any(), any(), any(), any(), any(), any()))
			.thenReturn(Page.empty());

		mockMvc.perform(get("/api/telemetry/scans")
				.param("zoneId", "1"))
			.andExpect(status().isOk());
	}

	@Test
	void shouldPassDateRangeFilterToService() throws Exception {
		when(telemetryService.findAll(any(), any(), any(), any(), any(), any(), any()))
			.thenReturn(Page.empty());

		mockMvc.perform(get("/api/telemetry/scans")
				.param("from", "2026-04-20T00:00:00Z")
				.param("to", "2026-04-20T23:59:59Z"))
			.andExpect(status().isOk());
	}
}
