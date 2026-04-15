package com.atpezms.atpezms.park.exception;

import com.atpezms.atpezms.common.exception.BusinessRuleViolationException;
import java.time.LocalDate;

/**
 * Thrown when a new seasonal period's date range overlaps an existing period.
 *
 * <p>Overlapping periods would produce ambiguous pricing classification:
 * a ticket purchase date that falls in two periods could be priced as either
 * PEAK or OFF_PEAK depending on which row the query returns first.
 */
public class SeasonalPeriodDateConflictException extends BusinessRuleViolationException {
	public SeasonalPeriodDateConflictException(LocalDate startDate, LocalDate endDate) {
		super("SEASONAL_PERIOD_DATE_CONFLICT",
				"The date range [" + startDate + ", " + endDate
						+ "] overlaps an existing seasonal period");
	}
}
