package com.atpezms.atpezms.billing.entity;

/**
 * Lifecycle state of a Bill.
 *
 * OPEN  -- the visit is ongoing; transactions may still be recorded.
 * SETTLED -- checkout is complete; no further transactions allowed.
 */
public enum BillStatus {
	OPEN,
	SETTLED
}
