package com.atpezms.atpezms.telemetry.controller;

import com.atpezms.atpezms.telemetry.dto.RecordScanRequest;
import com.atpezms.atpezms.telemetry.dto.ScanEventResponse;
import com.atpezms.atpezms.telemetry.entity.ScanDecision;
import com.atpezms.atpezms.telemetry.entity.ScanPurpose;
import com.atpezms.atpezms.telemetry.service.TelemetryService;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin controller for telemetry operations.
 *
 * All business logic is delegated to TelemetryService.
 *
 * Note: @PreAuthorize annotations will be added when Phase 3.2 (Security) is implemented.
 * POST endpoint will require the role of the calling context.
 * GET endpoint will require ROLE_MANAGER or ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/telemetry")
@Validated
public class TelemetryController {

	private final TelemetryService telemetryService;

	public TelemetryController(TelemetryService telemetryService) {
		this.telemetryService = telemetryService;
	}

	/**
	 * Record a new scan event.
	 */
	@PostMapping("/scans")
	public ResponseEntity<ScanEventResponse> recordScan(
			@RequestBody @Valid RecordScanRequest request) {
		ScanEventResponse response = telemetryService.recordScan(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	/**
	 * Query scan events with optional filters.
	 * Maximum page size is limited to 100 to prevent DoS.
	 */
	@GetMapping("/scans")
	public ResponseEntity<Page<ScanEventResponse>> getScans(
			@RequestParam(required = false) String rfidTag,
			@RequestParam(required = false) Long zoneId,
			@RequestParam(required = false) ScanPurpose purpose,
			@RequestParam(required = false) ScanDecision decision,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@PageableDefault(size = 20) Pageable pageable) {
		if (pageable.getPageSize() > 100) {
			throw new IllegalArgumentException("Page size cannot exceed 100");
		}
		Page<ScanEventResponse> response = telemetryService.findAll(
			rfidTag, zoneId, purpose, decision, from, to, pageable);
		return ResponseEntity.ok(response);
	}
}
