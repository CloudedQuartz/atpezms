package com.atpezms.atpezms.telemetry.entity;

import com.atpezms.atpezms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Append-only log of every wristband scan event in the system.
 *
 * This is a flat log with no JPA relationships to other contexts.
 * All references are stored as scalar values.
 *
 * Immutability is enforced at three levels:
 * 1. Entity: no setters (append-only)
 * 2. API: no PUT/PATCH/DELETE endpoints
 * 3. Business: scan events are historical records
 */
@Entity
@Table(name = "scan_events")
public class ScanEvent extends BaseEntity {

	@Column(name = "timestamp", nullable = false)
	private Instant timestamp;

	@Column(name = "rfid_tag", nullable = false, length = 100)
	private String rfidTag;

	@Column(name = "device_identifier", nullable = false, length = 100)
	private String deviceIdentifier;

	@Column(name = "zone_id", nullable = false)
	private Long zoneId;

	@Enumerated(EnumType.STRING)
	@Column(name = "purpose", nullable = false, length = 30)
	private ScanPurpose purpose;

	@Enumerated(EnumType.STRING)
	@Column(name = "decision", nullable = false, length = 10)
	private ScanDecision decision;

	@Column(name = "reason", length = 500)
	private String reason;

	protected ScanEvent() {
	}

	public ScanEvent(Instant timestamp, String rfidTag, String deviceIdentifier,
	                 Long zoneId, ScanPurpose purpose, ScanDecision decision,
	                 String reason) {
		if (timestamp == null) {
			throw new IllegalArgumentException("timestamp must not be null");
		}
		if (rfidTag == null || rfidTag.isBlank()) {
			throw new IllegalArgumentException("rfidTag must not be blank");
		}
		if (deviceIdentifier == null || deviceIdentifier.isBlank()) {
			throw new IllegalArgumentException("deviceIdentifier must not be blank");
		}
		if (zoneId == null || zoneId <= 0) {
			throw new IllegalArgumentException("zoneId must be positive");
		}
		if (purpose == null) {
			throw new IllegalArgumentException("purpose must not be null");
		}
		if (decision == null) {
			throw new IllegalArgumentException("decision must not be null");
		}

		this.timestamp = timestamp;
		this.rfidTag = rfidTag;
		this.deviceIdentifier = deviceIdentifier;
		this.zoneId = zoneId;
		this.purpose = purpose;
		this.decision = decision;
		this.reason = reason;
	}

	// Getters only -- no setters (append-only log)

	public Instant getTimestamp() {
		return timestamp;
	}

	public String getRfidTag() {
		return rfidTag;
	}

	public String getDeviceIdentifier() {
		return deviceIdentifier;
	}

	public Long getZoneId() {
		return zoneId;
	}

	public ScanPurpose getPurpose() {
		return purpose;
	}

	public ScanDecision getDecision() {
		return decision;
	}

	public String getReason() {
		return reason;
	}
}
