package com.atpezms.atpezms.park.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/park/zones}.
 *
 * <h2>code format constraint</h2>
 * {@code code} is the stable, immutable human identifier for a zone. We restrict
 * it to uppercase letters, digits, and underscores ({@code [A-Z0-9_]+}) for two
 * reasons:
 * <ol>
 *   <li>Readability: codes appear in logs, documentation, and payloads. A
 *       consistent format makes them easy to recognize at a glance.</li>
 *   <li>Safety: disallowing spaces, dashes, and locale-sensitive characters
 *       prevents subtle bugs when codes are used as path segments or map keys.</li>
 * </ol>
 * Existing seed codes ({@code ADVENTURE}, {@code WATER}, etc.) already follow
 * this convention -- this validation formalizes it.
 *
 * <h2>active default</h2>
 * {@code active} is optional. If omitted ({@code null}), the service treats it
 * as {@code true} (zone is open by default when created).
 */
public record CreateZoneRequest(
		@NotBlank
		@Size(min = 1, max = 50)
		@Pattern(
				regexp = "[A-Z0-9_]+",
				message = "code must contain only uppercase letters, digits, and underscores")
		String code,

		@NotBlank
		@Size(min = 1, max = 100)
		String name,

		@Size(max = 500)
		String description,

		// null → service defaults to true
		Boolean active
) {}
