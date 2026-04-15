package com.atpezms.atpezms.park.dto;

import com.atpezms.atpezms.common.entity.SeasonType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Request body for {@code POST /api/park/seasonal-periods}.
 *
 * <h2>Why endDate >= startDate is a 422, not a 400</h2>
 * Both {@code startDate} and {@code endDate} are individually valid ISO dates.
 * The constraint "end must not precede start" is a cross-field domain rule,
 * not a single-field format constraint. Per {@code DESIGN.md §3.2}, 422 is
 * the correct status for well-formed input that violates a domain rule.
 * Bean Validation (400) handles missing/malformed fields; service logic (422)
 * handles cross-field domain constraints.
 */
public record CreateSeasonalPeriodRequest(
		@NotNull
		LocalDate startDate,

		@NotNull
		LocalDate endDate,

		@NotNull
		SeasonType seasonType
) {}
