package com.atpezms.atpezms.billing.entity;

/**
 * The bounded context that originated a transaction.
 *
 * Used for audit and reporting purposes. No PREPAID source --
 * ticket prepayments use TransactionType.PREPAID_DEPOSIT with source TICKET.
 */
public enum TransactionSource {
	TICKET,
	FOOD,
	MERCHANDISE,
	EVENT
}
