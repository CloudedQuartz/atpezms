package com.atpezms.atpezms.telemetry.dto;

import com.atpezms.atpezms.telemetry.entity.ScanEvent;
import java.time.Instant;

public record ScanEventResponse(
	Long id,
	Instant timestamp,
	String rfidTag,
	String deviceIdentifier,
	Long zoneId,
	String purpose,
	String decision,
	String reason,
	Instant createdAt
) {
	public static ScanEventResponse from(ScanEvent event) {
		return new ScanEventResponse(
			event.getId(),
			event.getTimestamp(),
			event.getRfidTag(),
			event.getDeviceIdentifier(),
			event.getZoneId(),
			event.getPurpose().name(),
			event.getDecision().name(),
			event.getReason(),
			event.getCreatedAt()
		);
	}
}
