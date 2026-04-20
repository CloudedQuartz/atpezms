package com.atpezms.atpezms.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atpezms.atpezms.telemetry.entity.ScanDecision;
import com.atpezms.atpezms.telemetry.entity.ScanPurpose;
import com.atpezms.atpezms.telemetry.repository.ScanEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for Telemetry (Phase 5).
 *
 * <h2>@SpringBootTest vs @WebMvcTest</h2>
 * These tests boot the full application context (including JPA, Flyway, and the
 * real service layer) and execute requests against an in-memory H2 database.
 * This verifies the full request-to-database round-trip.
 *
 * <h2>@Transactional</h2>
 * Each test runs inside a transaction that is rolled back after the test completes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TelemetryIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ScanEventRepository scanEventRepository;

	// -----------------------------------------------------------------------
	// Record scan events
	// -----------------------------------------------------------------------

	@Test
	void shouldPersistAllowedScan() throws Exception {
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
			.andExpect(jsonPath("$.rfidTag").value("RFID-ABC-123"))
			.andExpect(jsonPath("$.decision").value("ALLOWED"))
			.andExpect(jsonPath("$.reason").doesNotExist());

		assertThat(scanEventRepository.findByRfidTagOrderByTimestampDesc("RFID-ABC-123"))
			.hasSize(1);
	}

	@Test
	void shouldPersistDeniedScanWithReason() throws Exception {
		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-DEF-456",
					  "deviceIdentifier": "turnstile-ride-02",
					  "zoneId": 2,
					  "purpose": "RIDE_ENTRY",
					  "decision": "DENIED",
					  "reason": "Height requirement not met"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.decision").value("DENIED"))
			.andExpect(jsonPath("$.reason").value("Height requirement not met"));
	}

	@Test
	void shouldApplyDefaultReasonWhenDeniedWithoutReason() throws Exception {
		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-GHI-789",
					  "deviceIdentifier": "turnstile-ride-03",
					  "zoneId": 3,
					  "purpose": "RIDE_ENTRY",
					  "decision": "DENIED"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.reason").value("No reason provided"));
	}

	@Test
	void shouldPersistMultipleScansIndependently() throws Exception {
		// First scan
		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-MULTI-1",
					  "deviceIdentifier": "turnstile-01",
					  "zoneId": 1,
					  "purpose": "RIDE_ENTRY",
					  "decision": "ALLOWED"
					}
					"""))
			.andExpect(status().isCreated());

		// Second scan
		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-MULTI-2",
					  "deviceIdentifier": "turnstile-02",
					  "zoneId": 1,
					  "purpose": "RIDE_ENTRY",
					  "decision": "ALLOWED"
					}
					"""))
			.andExpect(status().isCreated());

		assertThat(scanEventRepository.count()).isEqualTo(2);
	}

	// -----------------------------------------------------------------------
	// Query scan events
	// -----------------------------------------------------------------------

	@Test
	void shouldReturnPaginatedScans() throws Exception {
		// Create some scan events
		for (int i = 0; i < 25; i++) {
			mockMvc.perform(post("/api/telemetry/scans")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{
						  "rfidTag": "RFID-PAGE-%d",
						  "deviceIdentifier": "device-%d",
						  "zoneId": 1,
						  "purpose": "RIDE_ENTRY",
						  "decision": "ALLOWED"
						}
						""".formatted(i, i)))
				.andExpect(status().isCreated());
		}

		// First page
		mockMvc.perform(get("/api/telemetry/scans")
				.param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(10))
			.andExpect(jsonPath("$.totalElements").value(25))
			.andExpect(jsonPath("$.totalPages").value(3));
	}

	@Test
	void shouldFilterScansByRfidTag() throws Exception {
		// Create scan with specific RFID
		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-FILTER-TARGET",
					  "deviceIdentifier": "device-1",
					  "zoneId": 1,
					  "purpose": "RIDE_ENTRY",
					  "decision": "ALLOWED"
					}
					"""))
			.andExpect(status().isCreated());

		// Create another scan
		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-OTHER",
					  "deviceIdentifier": "device-2",
					  "zoneId": 1,
					  "purpose": "RIDE_ENTRY",
					  "decision": "ALLOWED"
					}
					"""))
			.andExpect(status().isCreated());

		// Filter by rfidTag
		mockMvc.perform(get("/api/telemetry/scans")
				.param("rfidTag", "RFID-FILTER-TARGET"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].rfidTag").value("RFID-FILTER-TARGET"));
	}

	@Test
	void shouldFilterScansByZoneId() throws Exception {
		// Create scans in different zones
		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-Z1",
					  "deviceIdentifier": "device-1",
					  "zoneId": 1,
					  "purpose": "RIDE_ENTRY",
					  "decision": "ALLOWED"
					}
					"""))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-Z2",
					  "deviceIdentifier": "device-2",
					  "zoneId": 2,
					  "purpose": "RIDE_ENTRY",
					  "decision": "ALLOWED"
					}
					"""))
			.andExpect(status().isCreated());

		// Filter by zoneId
		mockMvc.perform(get("/api/telemetry/scans")
				.param("zoneId", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].zoneId").value(1));
	}

	@Test
	void shouldFilterScansByPurpose() throws Exception {
		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-RIDE",
					  "deviceIdentifier": "device-1",
					  "zoneId": 1,
					  "purpose": "RIDE_ENTRY",
					  "decision": "ALLOWED"
					}
					"""))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-POS",
					  "deviceIdentifier": "pos-1",
					  "zoneId": 1,
					  "purpose": "POS_SALE",
					  "decision": "ALLOWED"
					}
					"""))
			.andExpect(status().isCreated());

		// Filter by purpose
		mockMvc.perform(get("/api/telemetry/scans")
				.param("purpose", "RIDE_ENTRY"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].purpose").value("RIDE_ENTRY"));
	}

	@Test
	void shouldFilterScansByDecision() throws Exception {
		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-ALLOWED",
					  "deviceIdentifier": "device-1",
					  "zoneId": 1,
					  "purpose": "RIDE_ENTRY",
					  "decision": "ALLOWED"
					}
					"""))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/telemetry/scans")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "rfidTag": "RFID-DENIED",
					  "deviceIdentifier": "device-1",
					  "zoneId": 1,
					  "purpose": "RIDE_ENTRY",
					  "decision": "DENIED",
					  "reason": "No access"
					}
					"""))
			.andExpect(status().isCreated());

		// Filter by decision
		mockMvc.perform(get("/api/telemetry/scans")
				.param("decision", "ALLOWED"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].decision").value("ALLOWED"));
	}
}
