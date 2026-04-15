package com.atpezms.atpezms.park.dto;

import com.atpezms.atpezms.common.entity.SeasonType;
import com.atpezms.atpezms.park.entity.SeasonalPeriod;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Response DTO for a seasonal period.
 */
public record SeasonalPeriodResponse(
		Long id,
		LocalDate startDate,
		LocalDate endDate,
		SeasonType seasonType,
		Instant createdAt,
		Instant updatedAt
) {
	public static SeasonalPeriodResponse from(SeasonalPeriod period) {
		return new SeasonalPeriodResponse(
				period.getId(),
				period.getStartDate(),
				period.getEndDate(),
				period.getSeasonType(),
				period.getCreatedAt(),
				period.getUpdatedAt()
		);
	}
}
