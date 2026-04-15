package com.atpezms.atpezms.park.controller;

import com.atpezms.atpezms.park.dto.CreateParkConfigurationRequest;
import com.atpezms.atpezms.park.dto.ParkConfigurationResponse;
import com.atpezms.atpezms.park.service.ParkConfigurationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for park configuration management.
 *
 * <h2>Base path: {@code /api/park/configurations}</h2>
 *
 * <h2>Append-only history</h2>
 * Configurations are never updated or deleted. Each {@code POST} creates a new
 * version and deactivates the previous one. The full history is visible via
 * {@code GET /api/park/configurations} (ordered newest-first). This is
 * consistent with the audit requirements in {@code DESIGN.md §3.8}.
 *
 * <h2>No PUT/PATCH/DELETE</h2>
 * Intentionally absent. Modifying or deleting a past configuration would
 * silently rewrite the history of why park capacity was at a given level,
 * making operational audits unreliable.
 */
@RestController
@RequestMapping("/api/park/configurations")
@Validated
public class ParkConfigurationController {

	private final ParkConfigurationService configService;

	public ParkConfigurationController(ParkConfigurationService configService) {
		this.configService = configService;
	}

	/**
	 * Returns all park configurations, newest first.
	 *
	 * <p>{@code GET /api/park/configurations}
	 * <p>Requires: {@code ROLE_MANAGER} or {@code ROLE_ADMIN}
	 */
	// Requires: ROLE_MANAGER or ROLE_ADMIN
	@GetMapping
	public ResponseEntity<List<ParkConfigurationResponse>> listConfigurations() {
		return ResponseEntity.ok(configService.listConfigurations());
	}

	/**
	 * Returns the currently active park configuration.
	 *
	 * <p>{@code GET /api/park/configurations/active}
	 * <p>Requires: {@code ROLE_MANAGER}, {@code ROLE_ADMIN}, or {@code ROLE_TICKET_STAFF}
	 */
	// Requires: ROLE_MANAGER or ROLE_ADMIN or ROLE_TICKET_STAFF
	@GetMapping("/active")
	public ResponseEntity<ParkConfigurationResponse> getActiveConfiguration() {
		return ResponseEntity.ok(configService.getActiveConfiguration());
	}

	/**
	 * Creates a new park configuration and immediately activates it.
	 *
	 * <p>{@code POST /api/park/configurations}
	 *
	 * <p>The previous active configuration is atomically deactivated in the same
	 * transaction. If the new capacity would conflict with already-reserved future
	 * dates, the request is rejected with 422 {@code CAPACITY_REDUCTION_CONFLICT}.
	 *
	 * <p>Requires: {@code ROLE_ADMIN} or {@code ROLE_MANAGER}
	 */
	// Requires: ROLE_ADMIN or ROLE_MANAGER
	@PostMapping
	public ResponseEntity<ParkConfigurationResponse> createConfiguration(
			@RequestBody @Valid CreateParkConfigurationRequest request) {
		ParkConfigurationResponse response = configService.createAndActivate(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}
}
