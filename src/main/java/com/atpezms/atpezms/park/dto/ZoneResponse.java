package com.atpezms.atpezms.park.dto;

import com.atpezms.atpezms.park.entity.Zone;
import java.time.Instant;

/**
 * Response DTO for a single zone.
 *
 * <h2>Why a static factory instead of a constructor</h2>
 * {@code from(Zone)} makes the mapping intent explicit and keeps all
 * entity-to-DTO translation in one place. The controller and service never
 * access zone fields directly -- they just call {@code ZoneResponse.from(zone)}.
 */
public record ZoneResponse(
		Long id,
		String code,
		String name,
		String description,
		boolean active,
		Instant createdAt,
		Instant updatedAt
) {
	public static ZoneResponse from(Zone zone) {
		return new ZoneResponse(
				zone.getId(),
				zone.getCode(),
				zone.getName(),
				zone.getDescription(),
				zone.isActive(),
				zone.getCreatedAt(),
				zone.getUpdatedAt()
		);
	}
}
