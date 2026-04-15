package com.atpezms.atpezms.park.service;

import com.atpezms.atpezms.park.dto.CreateZoneRequest;
import com.atpezms.atpezms.park.dto.UpdateZoneRequest;
import com.atpezms.atpezms.park.dto.ZoneResponse;
import com.atpezms.atpezms.park.entity.Zone;
import com.atpezms.atpezms.park.exception.ZoneCodeAlreadyExistsException;
import com.atpezms.atpezms.park.exception.ZoneNotFoundException;
import com.atpezms.atpezms.park.repository.ZoneRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for zone management.
 *
 * <h2>Zone semantics recap</h2>
 * A zone is a logical area of the park (Adventure Zone, Water Zone, etc.).
 * {@code active = false} means the zone is operationally closed right now.
 * Entitlements are still issued for all zones regardless of active status --
 * Phase 6 (Rides) enforces the closure at scan time.
 *
 * <h2>Code immutability</h2>
 * Zone codes are immutable after creation. The {@link UpdateZoneRequest} DTO
 * does not include a {@code code} field -- there is simply no way to update it
 * through the API.
 *
 * <h2>No hard-delete</h2>
 * There is no delete operation. Zone IDs are referenced as plain integers across
 * multiple bounded contexts (Ticketing's {@code access_entitlements.zone_id},
 * and future Rides/Food/Merchandise tables). Those contexts store no DB foreign
 * key constraint to {@code zones(id)}, so a hard delete would silently leave
 * dangling integer references. Setting {@code active = false} is the only
 * supported "removal" operation.
 */
@Service
@Transactional(readOnly = true)
public class ZoneService {

	private final ZoneRepository zoneRepository;

	public ZoneService(ZoneRepository zoneRepository) {
		this.zoneRepository = zoneRepository;
	}

	/**
	 * Lists all zones ordered by code.
	 *
	 * @param activeOnly when {@code true}, returns only zones where
	 *                   {@code active = true}
	 */
	public List<ZoneResponse> listZones(boolean activeOnly) {
		List<Zone> zones = activeOnly
				? zoneRepository.findAllByActiveTrueOrderByCodeAsc()
				: zoneRepository.findAllByOrderByCodeAsc();
		return zones.stream().map(ZoneResponse::from).toList();
	}

	/**
	 * Returns a single zone by ID.
	 *
	 * @param id the zone primary key
	 * @throws ZoneNotFoundException if no zone exists with this ID
	 */
	public ZoneResponse getZone(Long id) {
		Zone zone = zoneRepository.findById(id)
				.orElseThrow(() -> new ZoneNotFoundException(id));
		return ZoneResponse.from(zone);
	}

	/**
	 * Creates a new zone.
	 *
	 * <h2>Uniqueness check pattern (TOCTOU awareness)</h2>
	 * The {@code existsByCode} pre-check produces a friendly 409 for the common
	 * case (e.g., operator retries a form). However, two concurrent requests can
	 * both pass this check before either commits, resulting in a unique-constraint
	 * violation at flush time. We catch {@code DataIntegrityViolationException}
	 * and rethrow it as {@code ZoneCodeAlreadyExistsException} so the client
	 * always receives a structured 409 rather than a generic 500.
	 *
	 * <p>We use {@code saveAndFlush} to trigger the constraint check immediately
	 * inside the method (before the response is built), so the exception is caught
	 * here rather than propagating through Spring's proxy after the method returns.
	 *
	 * @param request validated creation request
	 * @return the saved zone DTO
	 * @throws ZoneCodeAlreadyExistsException if the code is taken
	 */
	@Transactional
	public ZoneResponse createZone(CreateZoneRequest request) {
		if (zoneRepository.existsByCode(request.code())) {
			throw new ZoneCodeAlreadyExistsException(request.code());
		}
		boolean active = request.active() != null ? request.active() : true;
		Zone zone = new Zone(request.code(), request.name(), request.description(), active);
		try {
			Zone saved = zoneRepository.saveAndFlush(zone);
			return ZoneResponse.from(saved);
		} catch (DataIntegrityViolationException ex) {
			// Concurrent request won the race on the unique constraint.
			throw new ZoneCodeAlreadyExistsException(request.code());
		}
	}

	/**
	 * Updates the mutable fields of an existing zone.
	 *
	 * <h2>PUT semantics</h2>
	 * The client replaces the entire mutable state. Omitting {@code description}
	 * explicitly clears it (sets to null).
	 *
	 * <h2>Flush before mapping</h2>
	 * JPA auditing ({@code @LastModifiedDate}) fires on the {@code @PreUpdate}
	 * lifecycle callback, which runs at flush time -- not when we call
	 * {@code zone.update()}. If we map to DTO immediately after mutating the
	 * entity (without flushing), the response would return the old
	 * {@code updatedAt}, which is incorrect.
	 *
	 * <p>Calling {@code saveAndFlush} after the mutation forces Hibernate to
	 * flush the dirty entity, triggering the {@code @PreUpdate} callback and
	 * populating the correct {@code updatedAt} before we build the response DTO.
	 *
	 * @param id      the zone to update
	 * @param request the new mutable state
	 * @return the updated zone DTO with current timestamps
	 * @throws ZoneNotFoundException if no zone exists with this ID
	 */
	@Transactional
	public ZoneResponse updateZone(Long id, UpdateZoneRequest request) {
		Zone zone = zoneRepository.findById(id)
				.orElseThrow(() -> new ZoneNotFoundException(id));
		zone.update(request.name(), request.description(), request.active());
		// saveAndFlush: triggers @PreUpdate (auditing timestamps) immediately
		// so the response DTO reflects the current updatedAt, not the stale one.
		Zone saved = zoneRepository.saveAndFlush(zone);
		return ZoneResponse.from(saved);
	}
}
