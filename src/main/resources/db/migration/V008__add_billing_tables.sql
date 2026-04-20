-- Phase 4 (Billing): Financial ledger tables.
--
-- bills: One bill per visit. Maintains running totals (materialized aggregate)
--        so that checkout / exit-gate reads are O(1) instead of O(N).
--
-- transactions: Append-only ledger. Every charge, prepayment, and settlement
--               is recorded here. Immutability is enforced at the entity level
--               (no setters) and at the API level (no PUT/PATCH/DELETE).

CREATE TABLE bills (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    visit_id             BIGINT       NOT NULL,
    total_charges_cents  BIGINT       NOT NULL DEFAULT 0,
    prepayment_cents     BIGINT       NOT NULL DEFAULT 0,
    settled_amount_cents BIGINT       NOT NULL DEFAULT 0,
    status               VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    settled_at           TIMESTAMP,
    created_at           TIMESTAMP    NOT NULL,
    updated_at           TIMESTAMP    NOT NULL,
    CONSTRAINT pk_bills PRIMARY KEY (id),
    CONSTRAINT uk_bills_visit_id UNIQUE (visit_id),
    CONSTRAINT chk_bills_status CHECK (status IN ('OPEN', 'SETTLED')),
    CONSTRAINT chk_bills_total_nonneg CHECK (total_charges_cents >= 0),
    CONSTRAINT chk_bills_prepayment_nonneg CHECK (prepayment_cents >= 0),
    CONSTRAINT chk_bills_settled_nonneg CHECK (settled_amount_cents >= 0)
);

CREATE TABLE transactions (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    bill_id       BIGINT       NOT NULL,
    type          VARCHAR(30)  NOT NULL,
    source        VARCHAR(30)  NOT NULL,
    description   VARCHAR(500) NOT NULL,
    amount_cents  BIGINT       NOT NULL,
    currency      VARCHAR(3)   NOT NULL DEFAULT 'USD',
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT fk_transactions_bill FOREIGN KEY (bill_id) REFERENCES bills(id),
    CONSTRAINT chk_transactions_type CHECK (type IN ('CHARGE', 'PREPAID_DEPOSIT')),
    CONSTRAINT chk_transactions_source CHECK (source IN ('TICKET', 'FOOD', 'MERCHANDISE', 'EVENT')),
    CONSTRAINT chk_transactions_amount_nonzero CHECK (amount_cents != 0)
);

CREATE INDEX idx_bills_visit_id ON bills(visit_id);
CREATE INDEX idx_transactions_bill_id ON transactions(bill_id);
