package com.atpezms.atpezms.ticketing.controller;

import com.atpezms.atpezms.ticketing.dto.RfidActiveVisitResponse;
import com.atpezms.atpezms.ticketing.service.RfidResolutionService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Debug/integration endpoint for resolving RFID tags.
 *
 * <p>Design note: this endpoint is optional and exists primarily to support
 * early integration testing. Production scan-processing contexts will call the
 * Ticketing service contract directly once those contexts exist.
 */
@Validated
@RestController
@RequestMapping("/api/ticketing/rfid")
public class RfidResolutionController {
	private final RfidResolutionService rfidResolutionService;

	public RfidResolutionController(RfidResolutionService rfidResolutionService) {
		this.rfidResolutionService = rfidResolutionService;
	}

	// Requires: ROLE_TICKET_STAFF or ROLE_MANAGER (enforced once Identity slice is built)
	@GetMapping("/{rfidTag}/active-visit")
	public ResponseEntity<RfidActiveVisitResponse> getActiveVisitByRfidTag(
			@PathVariable("rfidTag") @NotBlank @Size(max = 64) String rfidTag
	) {
		return ResponseEntity.ok(rfidResolutionService.resolveActiveVisitByRfidTag(rfidTag));
	}
}
