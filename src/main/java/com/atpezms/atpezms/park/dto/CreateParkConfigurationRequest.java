package com.atpezms.atpezms.park.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/park/configurations}.
 *
 * <h2>Why maxDailyCapacity is an Integer (boxed) not int (primitive)</h2>
 * Jakarta Bean Validation's {@code @NotNull} has no effect on primitives
 * because a primitive can never be null -- the field would simply receive its
 * default value (0) if omitted from the JSON body. Using the boxed
 * {@code Integer} type means: if the client omits the field, Jackson
 * deserializes it as {@code null}, and {@code @NotNull} correctly rejects it
 * with a 400 validation error.
 *
 * <h2>Why @Min(1) and not @Positive</h2>
 * {@code @Min(1)} makes the constraint value explicit and visible in the
 * annotation rather than relying on the reader knowing that
 * {@code @Positive} means {@code >= 1}. Both work identically.
 */
public record CreateParkConfigurationRequest(
		@NotNull
		@Min(1)
		Integer maxDailyCapacity
) {}
