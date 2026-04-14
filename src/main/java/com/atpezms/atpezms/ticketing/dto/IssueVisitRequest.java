package com.atpezms.atpezms.ticketing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Request body for selling a ticket, associating a wristband, and starting a visit.
 */
public record IssueVisitRequest(
		@NotNull @Positive Long visitorId,
		@NotBlank @Size(max = 64) String rfidTag,
		@NotNull @Positive Long passTypeId,
		LocalDate visitDate
) {
}
