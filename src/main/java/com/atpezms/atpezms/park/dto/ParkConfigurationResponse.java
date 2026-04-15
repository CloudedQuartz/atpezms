package com.atpezms.atpezms.park.dto;

import com.atpezms.atpezms.park.entity.ParkConfiguration;
import java.time.Instant;

/**
 * Response DTO for a single park configuration record.
 */
public record ParkConfigurationResponse(
		Long id,
		boolean active,
		int maxDailyCapacity,
		Instant createdAt,
		Instant updatedAt
) {
	public static ParkConfigurationResponse from(ParkConfiguration config) {
		return new ParkConfigurationResponse(
				config.getId(),
				config.isActive(),
				config.getMaxDailyCapacity(),
				config.getCreatedAt(),
				config.getUpdatedAt()
		);
	}
}
