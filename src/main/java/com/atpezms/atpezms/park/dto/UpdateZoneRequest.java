package com.atpezms.atpezms.park.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/park/zones/{id}}.
 *
 * <h2>PUT semantics -- full replacement of mutable fields</h2>
 * PUT replaces the entire mutable state of a resource. For zones, the mutable
 * fields are {@code name}, {@code description}, and {@code active}. The client
 * must always send all three:
 * <ul>
 *   <li>Omitting {@code description} explicitly sets it to {@code null} (clears it).</li>
 *   <li>Omitting {@code active} is a validation failure (required).</li>
 *   <li>{@code code} is NOT in this DTO -- it is immutable and cannot be changed.</li>
 * </ul>
 *
 * <h2>Why not PATCH?</h2>
 * PATCH for partial updates would require documenting merge semantics
 * (which fields are present = update, absent = keep). PUT with explicit
 * null-to-clear is simpler and unambiguous in a course context.
 */
public record UpdateZoneRequest(
		@NotBlank
		@Size(min = 1, max = 100)
		String name,

		@Size(max = 500)
		String description,   // null clears the existing description

		@NotNull
		Boolean active
) {}
