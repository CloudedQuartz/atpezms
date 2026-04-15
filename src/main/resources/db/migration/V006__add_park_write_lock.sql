-- =============================================================================
-- V006: Phase 2 -- Add park_write_lock anchor table
--
-- Park configuration writes (creating new ParkConfigurations, creating or
-- deleting SeasonalPeriods) require serialization to maintain their invariants:
--   - exactly one active ParkConfiguration at a time
--   - no overlapping SeasonalPeriod date ranges
--
-- The problem with locking an existing row in those tables directly:
--   - seasonal_periods can be completely empty (all periods deleted), leaving
--     no row to lock.
--   - park_configurations could theoretically be in a corrupt state.
--
-- Solution: a purpose-built single-row table. Before any write to
-- park_configurations or seasonal_periods, the service acquires a
-- PESSIMISTIC_WRITE lock on this row. This forces all concurrent writes
-- through a single serialization point.
--
-- This table is never written to by application code (only by this migration).
-- =============================================================================

CREATE TABLE park_write_lock (
    id         INT       NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_park_write_lock PRIMARY KEY (id),
    -- Exactly one row must always exist (id=1). This CHECK prevents accidental
    -- insertion of additional rows, which would undermine the locking design.
    CONSTRAINT chk_park_write_lock_single_row CHECK (id = 1)
);

-- The single anchor row. Application code will SELECT ... FOR UPDATE this row.
INSERT INTO park_write_lock (id, created_at) VALUES (1, CURRENT_TIMESTAMP);
