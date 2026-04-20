-- Phase 5 (Telemetry): Append-only scan event log.
--
-- scan_events: Every wristband scan in the system is recorded here.
-- This is a flat log with no JPA relationships to other contexts.
-- Immutability is enforced at the entity level (no setters) and at
-- the API level (no PUT/PATCH/DELETE endpoints).
--
-- Consumers:
--   - Security (SE-3): duplicate/unauthorized RFID detection via rfid_tag lookups
--   - Analytics (FR-MG2): congestion heat map via zone_id + timestamp range queries

CREATE TABLE scan_events (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    timestamp           TIMESTAMP    NOT NULL,
    rfid_tag            VARCHAR(100) NOT NULL,
    device_identifier   VARCHAR(100) NOT NULL,
    zone_id             BIGINT       NOT NULL,
    purpose             VARCHAR(30)  NOT NULL,
    decision            VARCHAR(10)  NOT NULL,
    reason              VARCHAR(500),
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    CONSTRAINT pk_scan_events PRIMARY KEY (id),
    CONSTRAINT chk_scans_purpose CHECK (purpose IN (
        'RIDE_ENTRY', 'RIDE_EXIT', 'POS_SALE',
        'KIOSK_RESERVATION', 'GATE_ENTRY', 'GATE_EXIT'
    )),
    CONSTRAINT chk_scans_decision CHECK (decision IN ('ALLOWED', 'DENIED'))
);

-- Index for SE-3: find all scans for a given RFID tag (duplicate detection)
CREATE INDEX idx_scans_rfid_tag ON scan_events(rfid_tag);

-- Index for FR-MG2: count scans per zone within a time window (congestion heat map)
CREATE INDEX idx_scans_zone_timestamp ON scan_events(zone_id, timestamp);