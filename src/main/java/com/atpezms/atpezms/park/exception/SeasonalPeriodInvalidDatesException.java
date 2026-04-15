package com.atpezms.atpezms.park.exception;

import com.atpezms.atpezms.common.exception.BusinessRuleViolationException;
import java.time.LocalDate;

/**
 * Thrown when a seasonal period's {@code endDate} precedes its {@code startDate}.
 *
 * <p>This is a cross-field domain rule (not a single-field format error), so it
 * maps to 422 (Business Rule Violation) rather than 400 (Validation Failure).
 * Per {@code DESIGN.md §3.2}, 422 is used when "input is well-formed but violates
 * a domain rule". Both dates are individually valid ISO dates; only their
 * relationship is invalid.
 */
public class SeasonalPeriodInvalidDatesException extends BusinessRuleViolationException {
	public SeasonalPeriodInvalidDatesException(LocalDate startDate, LocalDate endDate) {
		super("SEASONAL_PERIOD_INVALID_DATES",
				"endDate (" + endDate + ") must be >= startDate (" + startDate + ")");
	}
}
