-- =============================================================================
-- V001: Phase 1 -- Park reference tables + Ticketing schema
--
-- This is the first migration Flyway will ever run on a clean database.
-- It creates every table Phase 1 (Ticketing) needs, seeds the minimal
-- reference/configuration data required for pricing and capacity enforcement,
-- and creates the indexes and uniqueness constraints that enforce invariants
-- at the database level.
--
-- Convention from IMPLEMENTATION.md:
--   Tables   : snake_case, plural
--   Columns  : snake_case
--   FKs      : <referenced_table_singular>_id
--   Indexes  : idx_<table>_<column(s)>
--   Unique   : uk_<table>_<column(s)>
-- =============================================================================


-- =============================================================================
-- SECTION 1: PARK REFERENCE TABLES
--
-- These tables are owned by the Park context but are seeded here because
-- Ticketing needs them from Day 1 for pricing and capacity enforcement.
-- Phase 2 will add CRUD management endpoints on top of this schema.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- zones
--
-- A logical area of the park (Adventure Zone, Water Zone, ...).
-- Ticketing uses zone IDs when creating AccessEntitlements that grant zone access.
-- Rides, Food, Merchandise, and Events will also reference zones by ID.
-- Storing a stable `code` column (unique, uppercase) means application code
-- can look up a known zone without relying on a fragile hard-coded numeric ID.
-- -----------------------------------------------------------------------------
CREATE TABLE zones (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(50)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    CONSTRAINT pk_zones PRIMARY KEY (id),
    CONSTRAINT uk_zones_code UNIQUE (code)
);

-- -----------------------------------------------------------------------------
-- park_configurations
--
-- Park-wide operational settings. The design rule (DESIGN.md §3.6) says
-- configuration is data, not constants. max_daily_capacity lives here, not
-- in application.properties, so managers can change it via an admin endpoint
-- without a code deployment.
--
-- Only one row may have active = TRUE at any time. We enforce this in the
-- application service layer (Phase 2 Park CRUD will add the management
-- endpoint). Phase 1 seeds exactly one active row directly.
-- -----------------------------------------------------------------------------
CREATE TABLE park_configurations (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    active             BOOLEAN     NOT NULL DEFAULT FALSE,
    max_daily_capacity INT         NOT NULL,
    created_at         TIMESTAMP   NOT NULL,
    updated_at         TIMESTAMP   NOT NULL,
    CONSTRAINT pk_park_configurations PRIMARY KEY (id),
    CONSTRAINT chk_park_configurations_capacity CHECK (max_daily_capacity > 0)
);

-- -----------------------------------------------------------------------------
-- seasonal_periods
--
-- Date ranges classified as PEAK or OFF_PEAK. Ticketing reads this table
-- during ticket issuance to select the correct price row from pass_type_prices.
-- Overlapping ranges would produce ambiguous pricing; the application enforces
-- non-overlap when managing periods (Phase 2). Phase 1 seeds non-overlapping
-- ranges directly.
-- -----------------------------------------------------------------------------
CREATE TABLE seasonal_periods (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    season_type VARCHAR(10) NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL,
    CONSTRAINT pk_seasonal_periods PRIMARY KEY (id),
    CONSTRAINT chk_seasonal_periods_dates  CHECK (end_date >= start_date),
    CONSTRAINT chk_seasonal_periods_type   CHECK (season_type IN ('PEAK', 'OFF_PEAK'))
);


-- =============================================================================
-- SECTION 2: TICKETING TABLES
-- =============================================================================

-- -----------------------------------------------------------------------------
-- visitors
--
-- A person who visits the park. Records persist across visits so a returning
-- visitor is the same row. PII columns (name, email, phone) are encrypted at
-- the application layer by a JPA AttributeConverter before being stored here;
-- the database sees only Base64-encoded ciphertext. The _enc suffix signals
-- this to any developer reading the schema directly.
--
-- date_of_birth and height_cm are stored plain because ride eligibility and
-- pricing computations need them on hot paths. They are still treated as
-- sensitive and must never appear in logs.
-- -----------------------------------------------------------------------------
CREATE TABLE visitors (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    first_name_enc VARCHAR(512) NOT NULL,
    last_name_enc  VARCHAR(512) NOT NULL,
    email_enc      VARCHAR(512),
    phone_enc      VARCHAR(512),
    date_of_birth  DATE         NOT NULL,
    height_cm      INT          NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    CONSTRAINT pk_visitors PRIMARY KEY (id),
    CONSTRAINT chk_visitors_height CHECK (height_cm > 0)
);

-- -----------------------------------------------------------------------------
-- wristbands
--
-- A physical RFID wristband. rfid_tag must be globally unique -- two
-- wristbands with the same tag would break RFID resolution.
--
-- Lifecycle (status column):
--   IN_STOCK   -- known to system, not yet assigned to any visit
--   ACTIVE     -- currently assigned to an active visit
--   DEACTIVATED -- retired (lost/stolen/end-of-life); must never be reused
--
-- The UNIQUE constraint on rfid_tag implicitly creates a unique index, which
-- makes the resolution lookup (RFID tag -> active visit) fast without a
-- separate CREATE INDEX. This is the PR-1 hot path: scan to decision < 1s.
-- -----------------------------------------------------------------------------
CREATE TABLE wristbands (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    rfid_tag   VARCHAR(64) NOT NULL,
    status     VARCHAR(15) NOT NULL DEFAULT 'IN_STOCK',
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL,
    CONSTRAINT pk_wristbands PRIMARY KEY (id),
    CONSTRAINT uk_wristbands_rfid_tag UNIQUE (rfid_tag),
    CONSTRAINT chk_wristbands_status CHECK (status IN ('IN_STOCK', 'ACTIVE', 'DEACTIVATED'))
);

-- -----------------------------------------------------------------------------
-- pass_types
--
-- Configuration records describing pass categories. These are not transactional
-- data; they change infrequently and are managed by admins.
--
-- code is the stable application-level identifier (e.g. SINGLE_DAY).
-- multi_day_count is only meaningful when code = MULTI_DAY.
-- active = FALSE means the pass is no longer sold but historical tickets
-- referencing it remain valid.
-- -----------------------------------------------------------------------------
CREATE TABLE pass_types (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    code            VARCHAR(20)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    multi_day_count INT,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT pk_pass_types PRIMARY KEY (id),
    CONSTRAINT uk_pass_types_code UNIQUE (code),
    CONSTRAINT chk_pass_types_code CHECK (code IN ('SINGLE_DAY', 'MULTI_DAY', 'RIDE_SPECIFIC', 'FAMILY', 'FAST_TRACK'))
);

-- -----------------------------------------------------------------------------
-- pass_type_prices
--
-- The explicit price matrix. One row per (pass_type, age_group, day_type,
-- season_type) combination. Ticketing selects the matching row during issuance
-- and copies the price into the ticket as a snapshot, so future price changes
-- do not rewrite ticket history.
--
-- The UNIQUE constraint ensures there is always exactly one price for any given
-- combination -- no ambiguity during lookup.
-- -----------------------------------------------------------------------------
CREATE TABLE pass_type_prices (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    pass_type_id  BIGINT      NOT NULL,
    age_group     VARCHAR(10) NOT NULL,
    day_type      VARCHAR(10) NOT NULL,
    season_type   VARCHAR(10) NOT NULL,
    price_cents   INT         NOT NULL,
    currency      VARCHAR(3)  NOT NULL,
    created_at    TIMESTAMP   NOT NULL,
    updated_at    TIMESTAMP   NOT NULL,
    CONSTRAINT pk_pass_type_prices PRIMARY KEY (id),
    CONSTRAINT fk_pass_type_prices_pass_type FOREIGN KEY (pass_type_id) REFERENCES pass_types (id),
    CONSTRAINT uk_pass_type_prices_matrix UNIQUE (pass_type_id, age_group, day_type, season_type),
    CONSTRAINT chk_pass_type_prices_age_group   CHECK (age_group   IN ('CHILD', 'ADULT', 'SENIOR')),
    CONSTRAINT chk_pass_type_prices_day_type    CHECK (day_type    IN ('WEEKDAY', 'WEEKEND')),
    CONSTRAINT chk_pass_type_prices_season_type CHECK (season_type IN ('PEAK', 'OFF_PEAK')),
    CONSTRAINT chk_pass_type_prices_amount      CHECK (price_cents > 0)
);

-- -----------------------------------------------------------------------------
-- tickets
--
-- The purchase record. price_paid_cents and currency are copied from
-- pass_type_prices at purchase time -- this is the "price snapshot" pattern.
-- It means changing prices in the future has no effect on existing tickets,
-- which is correct: a ticket is a contract for what the visitor paid.
-- -----------------------------------------------------------------------------
CREATE TABLE tickets (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    visitor_id       BIGINT      NOT NULL,
    pass_type_id     BIGINT      NOT NULL,
    visit_date       DATE        NOT NULL,
    valid_from       DATE        NOT NULL,
    valid_to         DATE        NOT NULL,
    price_paid_cents INT         NOT NULL,
    currency         VARCHAR(3)  NOT NULL,
    purchased_at     TIMESTAMP   NOT NULL,
    created_at       TIMESTAMP   NOT NULL,
    updated_at       TIMESTAMP   NOT NULL,
    CONSTRAINT pk_tickets PRIMARY KEY (id),
    CONSTRAINT fk_tickets_visitor   FOREIGN KEY (visitor_id)   REFERENCES visitors   (id),
    CONSTRAINT fk_tickets_pass_type FOREIGN KEY (pass_type_id) REFERENCES pass_types (id),
    CONSTRAINT chk_tickets_validity CHECK (valid_to >= valid_from)
);

-- -----------------------------------------------------------------------------
-- visits
--
-- One entry-to-exit session in the park. The status column drives the RFID
-- resolution query: we look for the ACTIVE visit attached to a wristband.
--
-- Index on (wristband_id, status) is the PR-1 hot path index: the resolution
-- query is essentially "WHERE wristband_id = ? AND status = 'ACTIVE'".
-- Putting both columns in the index means H2 (and any real DB later) can
-- satisfy this query with an index-only scan.
-- -----------------------------------------------------------------------------
CREATE TABLE visits (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    visitor_id   BIGINT      NOT NULL,
    wristband_id BIGINT      NOT NULL,
    ticket_id    BIGINT      NOT NULL,
    status       VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    started_at   TIMESTAMP   NOT NULL,
    ended_at     TIMESTAMP,
    created_at   TIMESTAMP   NOT NULL,
    updated_at   TIMESTAMP   NOT NULL,
    CONSTRAINT pk_visits PRIMARY KEY (id),
    CONSTRAINT fk_visits_visitor   FOREIGN KEY (visitor_id)   REFERENCES visitors   (id),
    CONSTRAINT fk_visits_wristband FOREIGN KEY (wristband_id) REFERENCES wristbands (id),
    CONSTRAINT fk_visits_ticket    FOREIGN KEY (ticket_id)    REFERENCES tickets    (id),
    CONSTRAINT chk_visits_status   CHECK (status IN ('ACTIVE', 'ENDED'))
);

CREATE INDEX idx_visits_wristband_status ON visits (wristband_id, status);

-- -----------------------------------------------------------------------------
-- access_entitlements
--
-- What a Ticket grants. Stored as a flexible row per entitlement rather than
-- a fixed set of boolean columns, so new entitlement types (e.g. VIP lounge
-- access) can be added without schema changes.
--
-- entitlement_type drives which of the nullable FK columns is relevant:
--   ZONE          -> zone_id is set, ride_id and priority_level are null
--   RIDE          -> ride_id is set (ride entity lives in Rides slice, Phase 6)
--   QUEUE_PRIORITY -> priority_level is set
--
-- zone_id and ride_id are stored as plain BIGINT (not FK constraints) because
-- the referenced entities belong to different bounded contexts. Cross-context
-- JPA relationships are explicitly forbidden (DESIGN.md §6.2). The application
-- validates that the IDs exist via service-layer calls.
-- -----------------------------------------------------------------------------
CREATE TABLE access_entitlements (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    ticket_id        BIGINT      NOT NULL,
    entitlement_type VARCHAR(20) NOT NULL,
    zone_id          BIGINT,
    ride_id          BIGINT,
    priority_level   INT,
    created_at       TIMESTAMP   NOT NULL,
    updated_at       TIMESTAMP   NOT NULL,
    CONSTRAINT pk_access_entitlements PRIMARY KEY (id),
    CONSTRAINT fk_access_entitlements_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id),
    CONSTRAINT chk_access_entitlements_type CHECK (entitlement_type IN ('ZONE', 'RIDE', 'QUEUE_PRIORITY'))
);

-- -----------------------------------------------------------------------------
-- park_day_capacity
--
-- One row per calendar date. Tracks how many tickets have been issued for
-- that day against the maximum capacity that was in effect when the day row
-- was created (copied from park_configurations.max_daily_capacity).
--
-- Capacity enforcement uses a guarded atomic UPDATE:
--   UPDATE park_day_capacity
--      SET issued_count = issued_count + 1
--    WHERE visit_date = ? AND issued_count < max_capacity
-- If 0 rows are affected the day is sold out (422 CAPACITY_EXCEEDED).
-- This is safe under concurrency: the database serializes the UPDATE at the
-- row level, so two concurrent requests cannot both read "not full" and both
-- succeed when only one slot remains.
-- -----------------------------------------------------------------------------
CREATE TABLE park_day_capacity (
    id            BIGINT    NOT NULL AUTO_INCREMENT,
    visit_date    DATE      NOT NULL,
    max_capacity  INT       NOT NULL,
    issued_count  INT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    CONSTRAINT pk_park_day_capacity PRIMARY KEY (id),
    CONSTRAINT uk_park_day_capacity_date UNIQUE (visit_date),
    CONSTRAINT chk_park_day_capacity_max   CHECK (max_capacity > 0),
    CONSTRAINT chk_park_day_capacity_count CHECK (issued_count >= 0)
);


-- =============================================================================
-- SECTION 3: SEED DATA
--
-- Placeholder values for development. These are the minimum rows needed for
-- the application to start correctly and for tests to run. They are not
-- authoritative business data and will be revised once real values are agreed.
-- =============================================================================

-- Zones (starter set; Park Phase 2 will add management endpoints)
INSERT INTO zones (code, name, created_at, updated_at) VALUES
    ('ADVENTURE',   'Adventure Zone',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('WATER',       'Water Zone',        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('FOOD_COURT',  'Food Court Area',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('EVENTS',      'Events Arena',      CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ENTRANCE',    'Entrance & Exit',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Park configuration (one active row; max_daily_capacity is a placeholder)
INSERT INTO park_configurations (active, max_daily_capacity, created_at, updated_at) VALUES
    (TRUE, 5000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Seasonal periods (2026 calendar placeholder; covers this academic year)
INSERT INTO seasonal_periods (start_date, end_date, season_type, created_at, updated_at) VALUES
    ('2026-01-01', '2026-03-31', 'OFF_PEAK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('2026-04-01', '2026-04-30', 'PEAK',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('2026-05-01', '2026-08-31', 'PEAK',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('2026-09-01', '2026-11-30', 'OFF_PEAK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('2026-12-01', '2026-12-31', 'PEAK',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Pass types (starter set matching FR-VT1)
INSERT INTO pass_types (code, name, description, multi_day_count, active, created_at, updated_at) VALUES
    ('SINGLE_DAY',   'Single-Day Pass',      'Full park access for one day.',                                    NULL, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MULTI_DAY',    'Multi-Day Pass',        'Full park access across multiple consecutive days.',               3,    TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('RIDE_SPECIFIC','Ride-Specific Pass',    'Access to a curated set of rides for one day.',                   NULL, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('FAMILY',       'Family Pass',           'Discounted group rate; each member gets an individual ticket.',   NULL, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('FAST_TRACK',   'Fast-Track Pass',       'Single-day access with queue priority on all eligible rides.',    NULL, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Price matrix (placeholder prices in LKR cents; all 60 combinations)
-- Format: (pass_type_id, age_group, day_type, season_type, price_cents, currency)
-- IDs are resolved by pass type code via scalar subqueries to avoid coupling
-- this seed data to auto-increment insertion order.
INSERT INTO pass_type_prices (pass_type_id, age_group, day_type, season_type, price_cents, currency, created_at, updated_at) VALUES
    ((SELECT id FROM pass_types WHERE code = 'SINGLE_DAY'),    'CHILD',  'WEEKDAY', 'OFF_PEAK', 150000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'SINGLE_DAY'),    'CHILD',  'WEEKDAY', 'PEAK',     200000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'SINGLE_DAY'),    'CHILD',  'WEEKEND', 'OFF_PEAK', 175000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'SINGLE_DAY'),    'CHILD',  'WEEKEND', 'PEAK',     225000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'SINGLE_DAY'),    'ADULT',  'WEEKDAY', 'OFF_PEAK', 250000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'SINGLE_DAY'),    'ADULT',  'WEEKDAY', 'PEAK',     350000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'SINGLE_DAY'),    'ADULT',  'WEEKEND', 'OFF_PEAK', 300000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'SINGLE_DAY'),    'ADULT',  'WEEKEND', 'PEAK',     400000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'SINGLE_DAY'),    'SENIOR', 'WEEKDAY', 'OFF_PEAK', 200000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'SINGLE_DAY'),    'SENIOR', 'WEEKDAY', 'PEAK',     275000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'SINGLE_DAY'),    'SENIOR', 'WEEKEND', 'OFF_PEAK', 225000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'SINGLE_DAY'),    'SENIOR', 'WEEKEND', 'PEAK',     325000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'MULTI_DAY'),     'CHILD',  'WEEKDAY', 'OFF_PEAK', 400000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'MULTI_DAY'),     'CHILD',  'WEEKDAY', 'PEAK',     525000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'MULTI_DAY'),     'CHILD',  'WEEKEND', 'OFF_PEAK', 450000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'MULTI_DAY'),     'CHILD',  'WEEKEND', 'PEAK',     575000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'MULTI_DAY'),     'ADULT',  'WEEKDAY', 'OFF_PEAK', 650000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'MULTI_DAY'),     'ADULT',  'WEEKDAY', 'PEAK',     875000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'MULTI_DAY'),     'ADULT',  'WEEKEND', 'OFF_PEAK', 750000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'MULTI_DAY'),     'ADULT',  'WEEKEND', 'PEAK',     975000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'MULTI_DAY'),     'SENIOR', 'WEEKDAY', 'OFF_PEAK', 500000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'MULTI_DAY'),     'SENIOR', 'WEEKDAY', 'PEAK',     675000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'MULTI_DAY'),     'SENIOR', 'WEEKEND', 'OFF_PEAK', 575000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'MULTI_DAY'),     'SENIOR', 'WEEKEND', 'PEAK',     775000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'RIDE_SPECIFIC'), 'CHILD',  'WEEKDAY', 'OFF_PEAK', 100000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'RIDE_SPECIFIC'), 'CHILD',  'WEEKDAY', 'PEAK',     130000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'RIDE_SPECIFIC'), 'CHILD',  'WEEKEND', 'OFF_PEAK', 115000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'RIDE_SPECIFIC'), 'CHILD',  'WEEKEND', 'PEAK',     145000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'RIDE_SPECIFIC'), 'ADULT',  'WEEKDAY', 'OFF_PEAK', 175000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'RIDE_SPECIFIC'), 'ADULT',  'WEEKDAY', 'PEAK',     225000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'RIDE_SPECIFIC'), 'ADULT',  'WEEKEND', 'OFF_PEAK', 200000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'RIDE_SPECIFIC'), 'ADULT',  'WEEKEND', 'PEAK',     250000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'RIDE_SPECIFIC'), 'SENIOR', 'WEEKDAY', 'OFF_PEAK', 140000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'RIDE_SPECIFIC'), 'SENIOR', 'WEEKDAY', 'PEAK',     180000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'RIDE_SPECIFIC'), 'SENIOR', 'WEEKEND', 'OFF_PEAK', 160000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'RIDE_SPECIFIC'), 'SENIOR', 'WEEKEND', 'PEAK',     200000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAMILY'),        'CHILD',  'WEEKDAY', 'OFF_PEAK', 120000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAMILY'),        'CHILD',  'WEEKDAY', 'PEAK',     160000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAMILY'),        'CHILD',  'WEEKEND', 'OFF_PEAK', 140000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAMILY'),        'CHILD',  'WEEKEND', 'PEAK',     180000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAMILY'),        'ADULT',  'WEEKDAY', 'OFF_PEAK', 210000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAMILY'),        'ADULT',  'WEEKDAY', 'PEAK',     290000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAMILY'),        'ADULT',  'WEEKEND', 'OFF_PEAK', 245000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAMILY'),        'ADULT',  'WEEKEND', 'PEAK',     330000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAMILY'),        'SENIOR', 'WEEKDAY', 'OFF_PEAK', 160000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAMILY'),        'SENIOR', 'WEEKDAY', 'PEAK',     220000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAMILY'),        'SENIOR', 'WEEKEND', 'OFF_PEAK', 180000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAMILY'),        'SENIOR', 'WEEKEND', 'PEAK',     260000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAST_TRACK'),    'CHILD',  'WEEKDAY', 'OFF_PEAK', 200000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAST_TRACK'),    'CHILD',  'WEEKDAY', 'PEAK',     270000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAST_TRACK'),    'CHILD',  'WEEKEND', 'OFF_PEAK', 230000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAST_TRACK'),    'CHILD',  'WEEKEND', 'PEAK',     310000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAST_TRACK'),    'ADULT',  'WEEKDAY', 'OFF_PEAK', 350000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAST_TRACK'),    'ADULT',  'WEEKDAY', 'PEAK',     475000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAST_TRACK'),    'ADULT',  'WEEKEND', 'OFF_PEAK', 400000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAST_TRACK'),    'ADULT',  'WEEKEND', 'PEAK',     525000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAST_TRACK'),    'SENIOR', 'WEEKDAY', 'OFF_PEAK', 275000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAST_TRACK'),    'SENIOR', 'WEEKDAY', 'PEAK',     375000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAST_TRACK'),    'SENIOR', 'WEEKEND', 'OFF_PEAK', 315000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM pass_types WHERE code = 'FAST_TRACK'),    'SENIOR', 'WEEKEND', 'PEAK',     425000, 'LKR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
