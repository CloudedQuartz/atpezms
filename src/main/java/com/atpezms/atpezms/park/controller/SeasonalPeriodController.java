package com.atpezms.atpezms.park.controller;

import com.atpezms.atpezms.park.dto.CreateSeasonalPeriodRequest;
import com.atpezms.atpezms.park.dto.SeasonalPeriodResponse;
import com.atpezms.atpezms.park.service.SeasonalPeriodService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for seasonal period management.
 *
 * <h2>Base path: {@code /api/park/seasonal-periods}</h2>
 *
 * <h2>Immutability -- no PUT/PATCH</h2>
 * Seasonal periods are immutable after creation. If a period was created with
 * incorrect dates or season type, delete it and create a new one. This
 * preserves the integrity of the pricing history (see service Javadoc).
 */
@RestController
@RequestMapping("/api/park/seasonal-periods")
@Validated
public class SeasonalPeriodController {

	private final SeasonalPeriodService periodService;

	public SeasonalPeriodController(SeasonalPeriodService periodService) {
		this.periodService = periodService;
	}

	/**
	 * Returns all seasonal periods ordered by start date ascending.
	 *
	 * <p>{@code GET /api/park/seasonal-periods}
	 * <p>Requires: {@code ROLE_MANAGER} or {@code ROLE_ADMIN}
	 */
	// Requires: ROLE_MANAGER or ROLE_ADMIN
	@GetMapping
	public ResponseEntity<List<SeasonalPeriodResponse>> listPeriods() {
		return ResponseEntity.ok(periodService.listPeriods());
	}

	/**
	 * Returns a single seasonal period by ID.
	 *
	 * <p>{@code GET /api/park/seasonal-periods/{id}}
	 * <p>Requires: {@code ROLE_MANAGER} or {@code ROLE_ADMIN}
	 */
	// Requires: ROLE_MANAGER or ROLE_ADMIN
	@GetMapping("/{id}")
	public ResponseEntity<SeasonalPeriodResponse> getPeriod(@PathVariable Long id) {
		return ResponseEntity.ok(periodService.getPeriod(id));
	}

	/**
	 * Creates a new seasonal period.
	 *
	 * <p>{@code POST /api/park/seasonal-periods}
	 * <p>Requires: {@code ROLE_ADMIN} or {@code ROLE_MANAGER}
	 */
	// Requires: ROLE_ADMIN or ROLE_MANAGER
	@PostMapping
	public ResponseEntity<SeasonalPeriodResponse> createPeriod(
			@RequestBody @Valid CreateSeasonalPeriodRequest request) {
		SeasonalPeriodResponse response = periodService.createPeriod(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	/**
	 * Deletes a seasonal period.
	 *
	 * <p>{@code DELETE /api/park/seasonal-periods/{id}}
	 * <p>Requires: {@code ROLE_ADMIN} or {@code ROLE_MANAGER}
	 */
	// Requires: ROLE_ADMIN or ROLE_MANAGER
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deletePeriod(@PathVariable Long id) {
		periodService.deletePeriod(id);
		return ResponseEntity.noContent().build();
	}
}
