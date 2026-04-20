package com.atpezms.atpezms.billing.entity;

/**
 * Distinguishes between charges and prepayments in the ledger.
 *
 * CHARGE           -- adds to the running total (food, merchandise, events, tickets).
 * PREPAID_DEPOSIT  -- reduces the outstanding balance (ticket prepayment).
 *
 * Credits (refunds) are expressed as negative CHARGE transactions,
 * not as a separate type.
 */
public enum TransactionType {
	CHARGE,
	PREPAID_DEPOSIT
}
