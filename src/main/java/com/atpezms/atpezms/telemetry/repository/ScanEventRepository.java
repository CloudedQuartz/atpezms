package com.atpezms.atpezms.telemetry.repository;

import com.atpezms.atpezms.telemetry.entity.ScanDecision;
import com.atpezms.atpezms.telemetry.entity.ScanEvent;
import com.atpezms.atpezms.telemetry.entity.ScanPurpose;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScanEventRepository extends JpaRepository<ScanEvent, Long> {

	/**
	 * Find all scans for a given RFID tag, ordered by timestamp descending.
	 * Used by SE-3 (duplicate/unauthorized RFID detection).
	 */
	List<ScanEvent> findByRfidTagOrderByTimestampDesc(String rfidTag);

	/**
	 * Find scans in a specific zone within a time window.
	 * Used by FR-MG2 (congestion heat map).
	 */
	List<ScanEvent> findByZoneIdAndTimestampBetweenOrderByTimestampAsc(
		Long zoneId, Instant from, Instant to);

	/**
	 * Paginated query with dynamic filters.
	 * Uses JPQL with COALESCE to treat null as "don't filter".
	 */
	@Query("SELECT s FROM ScanEvent s WHERE " +
	       "(:rfidTag IS NULL OR s.rfidTag = :rfidTag) AND " +
	       "(:zoneId IS NULL OR s.zoneId = :zoneId) AND " +
	       "(:purpose IS NULL OR s.purpose = :purpose) AND " +
	       "(:decision IS NULL OR s.decision = :decision) AND " +
	       "(:from IS NULL OR s.timestamp >= :from) AND " +
	       "(:to IS NULL OR s.timestamp <= :to)")
	Page<ScanEvent> findWithFilters(
		@Param("rfidTag") String rfidTag,
		@Param("zoneId") Long zoneId,
		@Param("purpose") ScanPurpose purpose,
		@Param("decision") ScanDecision decision,
		@Param("from") Instant from,
		@Param("to") Instant to,
		Pageable pageable);
}
