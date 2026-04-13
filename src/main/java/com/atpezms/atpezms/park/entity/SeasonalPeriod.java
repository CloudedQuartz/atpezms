package com.atpezms.atpezms.park.entity;

import com.atpezms.atpezms.common.entity.BaseEntity;
import com.atpezms.atpezms.common.entity.SeasonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Date range classified as PEAK vs OFF_PEAK.
 *
 * Ticketing uses these rows to determine the SeasonType for a visit date so it
 * can select the correct price from the pass_type_prices matrix.
 */
@Entity
@Table(name = "seasonal_periods")
public class SeasonalPeriod extends BaseEntity {
	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "season_type", nullable = false, length = 10)
	private SeasonType seasonType;

	protected SeasonalPeriod() {
		// For JPA
	}

	public SeasonalPeriod(LocalDate startDate, LocalDate endDate, SeasonType seasonType) {
		if (startDate == null) {
			throw new IllegalArgumentException("startDate is required");
		}
		if (endDate == null) {
			throw new IllegalArgumentException("endDate is required");
		}
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("endDate must be >= startDate");
		}
		if (seasonType == null) {
			throw new IllegalArgumentException("seasonType is required");
		}
		this.startDate = startDate;
		this.endDate = endDate;
		this.seasonType = seasonType;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public SeasonType getSeasonType() {
		return seasonType;
	}
}
