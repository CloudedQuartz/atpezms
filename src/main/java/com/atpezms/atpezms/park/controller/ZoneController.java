package com.atpezms.atpezms.park.controller;

import com.atpezms.atpezms.park.dto.CreateZoneRequest;
import com.atpezms.atpezms.park.dto.UpdateZoneRequest;
import com.atpezms.atpezms.park.dto.ZoneResponse;
import com.atpezms.atpezms.park.service.ZoneService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the zones resource.
 *
 * <h2>Base path: {@code /api/park/zones}</h2>
 *
 * <h2>Thin controller rule</h2>
 * This controller only: deserializes the request, triggers Bean Validation,
 * delegates to {@link ZoneService}, and maps the result to the correct HTTP
 * status. No business logic lives here.
 *
 * <h2>No DELETE endpoint</h2>
 * Zones are never hard-deleted because their IDs are referenced (as plain
 * integers) across multiple bounded contexts. Use {@code PUT} with
 * {@code active=false} to close a zone operationally.
 *
 * <h2>@Validated on the class</h2>
 * {@code @Validated} is required on the class to enable Bean Validation on
 * method parameters (path variables, query params). Without it, annotations
 * like {@code @Min} on a {@code @PathVariable} silently do nothing.
 * (IMPLEMENTATION.md §8.1)
 */
@RestController
@RequestMapping("/api/park/zones")
@Validated
public class ZoneController {

	private final ZoneService zoneService;

	public ZoneController(ZoneService zoneService) {
		this.zoneService = zoneService;
	}

	/**
	 * Lists all zones, ordered by code ascending.
	 *
	 * <p>{@code GET /api/park/zones}
	 * <p>{@code GET /api/park/zones?activeOnly=true} -- returns only active zones.
	 *
	 * <p>Requires: {@code ROLE_MANAGER} or {@code ROLE_ADMIN}
	 */
	// Requires: ROLE_MANAGER or ROLE_ADMIN
	@GetMapping
	public ResponseEntity<List<ZoneResponse>> listZones(
			@RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
		return ResponseEntity.ok(zoneService.listZones(activeOnly));
	}

	/**
	 * Returns a single zone by ID.
	 *
	 * <p>{@code GET /api/park/zones/{id}}
	 *
	 * <p>Requires: {@code ROLE_MANAGER} or {@code ROLE_ADMIN}
	 */
	// Requires: ROLE_MANAGER or ROLE_ADMIN
	@GetMapping("/{id}")
	public ResponseEntity<ZoneResponse> getZone(@PathVariable Long id) {
		return ResponseEntity.ok(zoneService.getZone(id));
	}

	/**
	 * Creates a new zone.
	 *
	 * <p>{@code POST /api/park/zones}
	 *
	 * <p>Requires: {@code ROLE_ADMIN} or {@code ROLE_MANAGER}
	 */
	// Requires: ROLE_ADMIN or ROLE_MANAGER
	@PostMapping
	public ResponseEntity<ZoneResponse> createZone(
			@RequestBody @Valid CreateZoneRequest request) {
		ZoneResponse response = zoneService.createZone(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	/**
	 * Updates the mutable fields of an existing zone.
	 *
	 * <p>{@code PUT /api/park/zones/{id}}
	 *
	 * <p>PUT is a full replacement of mutable state (name, description, active).
	 * Omitting {@code description} clears it. {@code code} cannot be updated.
	 *
	 * <p>Requires: {@code ROLE_ADMIN} or {@code ROLE_MANAGER}
	 */
	// Requires: ROLE_ADMIN or ROLE_MANAGER
	@PutMapping("/{id}")
	public ResponseEntity<ZoneResponse> updateZone(
			@PathVariable Long id,
			@RequestBody @Valid UpdateZoneRequest request) {
		return ResponseEntity.ok(zoneService.updateZone(id, request));
	}
}
