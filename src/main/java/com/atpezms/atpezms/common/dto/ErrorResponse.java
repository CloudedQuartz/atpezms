package com.atpezms.atpezms.common.dto;

import java.time.Instant;
import java.util.List;

/**
 * Standard error payload for all API failures.
 *
 * Why we centralize this:
 * - Clients (POS terminals, turnstiles, staff dashboards) must be able to parse errors consistently.
 * - Controllers should not handcraft error bodies; they just throw exceptions.
 */
public record ErrorResponse(
		int status,
		String code,
		String message,
		Instant timestamp,
		List<FieldError> fieldErrors
) {
	public record FieldError(String field, String message) {}
}
