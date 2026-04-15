-- =============================================================================
-- V005: Phase 2 -- Add description and active columns to zones
--
-- Phase 1 seeded zones with only code and name. Phase 2 introduces full zone
-- management. Two new columns are added:
--
--   description  -- human-readable description for staff dashboards (nullable)
--   active       -- operational open/closed flag (NOT NULL, defaults TRUE)
--
-- active = FALSE means the zone is physically closed (e.g. renovation).
-- It does not affect already-issued AccessEntitlements (they are immutable
-- snapshots). Phase 6 (Rides) will enforce zone closure at scan time by
-- checking this flag. New tickets still include entitlements for all zones
-- regardless of active status, because the visitor paid for full-park access.
--
-- Both columns are additive: existing seeded zones remain valid after migration.
-- =============================================================================

-- H2 does not support adding multiple columns in a single ALTER TABLE statement.
-- Each column addition requires its own ALTER TABLE statement.
ALTER TABLE zones ADD COLUMN description VARCHAR(500);
ALTER TABLE zones ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
