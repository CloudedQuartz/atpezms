-- =============================================================================
-- V004: Phase 1.3 follow-up -- Access entitlements hardening
--
-- Phase 1.3 makes entitlement rows part of the RFID scan hot path because
-- Ticketing resolves RFID -> active Visit -> Ticket -> entitlements.
--
-- This migration adds:
-- - an index on access_entitlements.ticket_id for fast lookup
-- - a CHECK constraint that enforces the entitlement-type specific column rules
--   at the database level (defense in depth)
--
-- NOTE: This CHECK is strict over existing rows. If a dev DB contains legacy or
-- hand-edited rows that violate these invariants, Flyway will fail until those
-- rows are fixed/removed.
-- =============================================================================

-- Hot path: fetch entitlements by ticket id.
CREATE INDEX idx_access_entitlements_ticket_id ON access_entitlements (ticket_id);

-- Enforce type-specific column invariants:
--   ZONE          -> zone_id set (positive); ride_id and priority_level null
--   RIDE          -> ride_id set (positive); zone_id and priority_level null
--   QUEUE_PRIORITY -> priority_level set (positive); zone_id and ride_id null
ALTER TABLE access_entitlements
    ADD CONSTRAINT chk_access_entitlements_fields CHECK (
        (entitlement_type = 'ZONE' AND zone_id IS NOT NULL AND zone_id > 0 AND ride_id IS NULL AND priority_level IS NULL)
        OR
        (entitlement_type = 'RIDE' AND ride_id IS NOT NULL AND ride_id > 0 AND zone_id IS NULL AND priority_level IS NULL)
        OR
        (entitlement_type = 'QUEUE_PRIORITY' AND priority_level IS NOT NULL AND priority_level > 0 AND zone_id IS NULL AND ride_id IS NULL)
    );
