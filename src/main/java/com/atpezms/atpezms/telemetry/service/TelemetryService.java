package com.atpezms.atpezms.telemetry.service;

import com.atpezms.atpezms.telemetry.dto.RecordScanRequest;
import com.atpezms.atpezms.telemetry.dto.ScanEventResponse;
import com.atpezms.atpezms.telemetry.entity.ScanDecision;
import com.atpezms.atpezms.telemetry.entity.ScanEvent;
import com.atpezms.atpezms.telemetry.entity.ScanPurpose;
import com.atpezms.atpezms.telemetry.repository.ScanEventRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core telemetry logic: record scan events and query them.
 *
 * This is an append-only logging service. There are no business rules
 * that can fail -- the service records what it is told.
 */
@Service
@Transactional(readOnly = true)
public class TelemetryService {

	private final ScanEventRepository scanEventRepository;
	private final Clock clock;

	public TelemetryService(ScanEventRepository scanEventRepository, Clock clock) {
		this.scanEventRepository = scanEventRepository;
		this.clock = clock;
	}

	/**
	 * Record a new scan event.
	 *
	 * Timestamp is set by the service using the injected Clock (not provided by
	 * the caller). This ensures consistent time handling and testability.
	 *
	 * If decision is DENIED and reason is blank, a defensive default is applied.
	 */
	@Transactional
	public ScanEventResponse recordScan(RecordScanRequest request) {
		Instant timestamp = Instant.now(clock);

		String reason = request.reason();
		if (request.decision() == ScanDecision.DENIED
				&& (reason == null || reason.isBlank())) {
			reason = "No reason provided";
		}

		ScanEvent event = new ScanEvent(
			timestamp,
			request.rfidTag(),
			request.deviceIdentifier(),
			request.zoneId(),
			request.purpose(),
			request.decision(),
			reason
		);

		scanEventRepository.save(event);
		return ScanEventResponse.from(event);
	}

	/**
	 * Query scan events with optional filters.
	 *
	 * All filter parameters are nullable. When null, that filter is not applied.
	 * Results are paginated to handle the append-only nature of the log.
	 */
	public Page<ScanEventResponse> findAll(
			String rfidTag,
			Long zoneId,
			ScanPurpose purpose,
			ScanDecision decision,
			Instant from,
			Instant to,
			Pageable pageable) {

		Page<ScanEvent> events = scanEventRepository.findWithFilters(
			rfidTag, zoneId, purpose, decision, from, to, pageable);

		return events.map(ScanEventResponse::from);
	}
}
