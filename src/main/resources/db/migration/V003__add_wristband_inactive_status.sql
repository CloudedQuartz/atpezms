-- =============================================================================
-- V003: Add INACTIVE to wristband status lifecycle
--
-- V001 defined three wristband states: IN_STOCK, ACTIVE, DEACTIVATED.
-- Phase 1.2 design introduced a fourth state:
--
--   INACTIVE -- the wristband has been issued to a specific visitor and is
--               physically on their wrist, but they are not currently inside
--               the park in an active Visit session (e.g. overnight between
--               days of a multi-day pass).
--
-- Why INACTIVE cannot be collapsed into IN_STOCK:
--   IN_STOCK means "in the stockroom, never issued, can be handed to any
--   visitor." INACTIVE means "already issued to someone; only re-activatable
--   for that visitor's existing valid ticket." Conflating the two would allow
--   a scan-processing context to treat a visitor's wristband as unclaimed.
--
-- Why INACTIVE cannot be collapsed into ACTIVE:
--   ACTIVE is the signal that the visitor is physically inside the park right
--   now. Every downstream context (Rides, Food, Merchandise) uses ACTIVE to
--   decide whether to allow an interaction. An ACTIVE wristband with no live
--   Visit behind it is an inconsistent state those contexts cannot handle.
--
-- No existing rows are affected; no data migration is required. The constraint
-- is widened to accept the new value. The transition INTO INACTIVE is
-- implemented in Phase 4 (checkout / Visit-end orchestration).
-- =============================================================================

ALTER TABLE wristbands DROP CONSTRAINT chk_wristbands_status;

ALTER TABLE wristbands
    ADD CONSTRAINT chk_wristbands_status
    CHECK (status IN ('IN_STOCK', 'ACTIVE', 'INACTIVE', 'DEACTIVATED'));
