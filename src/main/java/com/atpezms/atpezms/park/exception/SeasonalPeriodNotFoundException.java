package com.atpezms.atpezms.park.exception;

import com.atpezms.atpezms.common.exception.ResourceNotFoundException;

/** Thrown when a seasonal period lookup by ID fails. */
public class SeasonalPeriodNotFoundException extends ResourceNotFoundException {
	public SeasonalPeriodNotFoundException(Long id) {
		super("SEASONAL_PERIOD_NOT_FOUND", "Seasonal period not found: id=" + id);
	}
}
