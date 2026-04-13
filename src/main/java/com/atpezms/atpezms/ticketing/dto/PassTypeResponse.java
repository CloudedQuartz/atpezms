package com.atpezms.atpezms.ticketing.dto;

import com.atpezms.atpezms.ticketing.entity.PassType;
import com.atpezms.atpezms.ticketing.entity.PassTypeCode;

/**
 * Response DTO for a pass type.
 *
 * This is a Java record: an immutable data carrier with a compact constructor,
 * auto-generated equals/hashCode/toString, and accessor methods named after the
 * components (e.g., id(), code()). Records are ideal for response DTOs because
 * they carry no behaviour and must not be mutated after construction.
 *
 * The static factory method {@code from(PassType)} keeps mapping logic out of
 * the entity and out of the controller. The service layer calls it so the
 * controller receives a DTO, never a raw entity.
 */
public record PassTypeResponse(
		Long id,
		PassTypeCode code,
		String name,
		String description,
		Integer multiDayCount,
		boolean active) {

	public static PassTypeResponse from(PassType passType) {
		return new PassTypeResponse(
				passType.getId(),
				passType.getCode(),
				passType.getName(),
				passType.getDescription(),
				passType.getMultiDayCount(),
				passType.isActive());
	}
}
