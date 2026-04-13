-- =============================================================================
-- V002: Enforce pass_types.multi_day_count invariant
--
-- In V001 we modelled pass_types as configuration data. The invariant is:
--   - multi_day_count must be NULL unless code = 'MULTI_DAY'
--   - when code = 'MULTI_DAY', multi_day_count must be > 0
--
-- We enforce this at the database level so invalid rows cannot exist even if
-- application-level validation is bypassed.
-- =============================================================================

ALTER TABLE pass_types
    ADD CONSTRAINT chk_pass_types_multi_day_count
    CHECK (
        (code = 'MULTI_DAY' AND multi_day_count IS NOT NULL AND multi_day_count > 0)
        OR
        (code <> 'MULTI_DAY' AND multi_day_count IS NULL)
    );
