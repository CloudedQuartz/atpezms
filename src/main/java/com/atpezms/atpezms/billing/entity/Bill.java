package com.atpezms.atpezms.billing.entity;

import com.atpezms.atpezms.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One Bill per Visit. Maintains running totals (materialized aggregate)
 * so that checkout / exit-gate reads are O(1) instead of O(N).
 *
 * The balance is derived on demand:
 *   balanceCents = totalChargesCents - prepaymentCents - settledAmountCents
 *
 * This avoids a 4-way stored invariant that could drift.
 */
@Entity
@Table(name = "bills")
public class Bill extends BaseEntity {

	@Column(name = "visit_id", nullable = false, unique = true)
	private Long visitId;

	@Column(name = "total_charges_cents", nullable = false)
	private long totalChargesCents = 0;

	@Column(name = "prepayment_cents", nullable = false)
	private long prepaymentCents = 0;

	@Column(name = "settled_amount_cents", nullable = false)
	private long settledAmountCents = 0;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private BillStatus status = BillStatus.OPEN;

	@Column(name = "settled_at")
	private Instant settledAt;

	@OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Transaction> transactions = new ArrayList<>();

	protected Bill() {
	}

	public Bill(Long visitId) {
		if (visitId == null || visitId <= 0) {
			throw new IllegalArgumentException("visitId must be positive");
		}
		this.visitId = visitId;
	}

	/**
	 * Derived balance: charges minus prepayments minus what was actually paid.
	 * Not stored as a column to avoid invariant drift.
	 */
	public long getBalanceCents() {
		return totalChargesCents - prepaymentCents - settledAmountCents;
	}

	/**
	 * Add a charge to the running total. Only allowed while OPEN.
	 */
	public void addCharge(long amountCents) {
		if (status != BillStatus.OPEN) {
			throw new IllegalStateException("Cannot add charge to a settled bill");
		}
		if (totalChargesCents + amountCents < 0) {
			throw new IllegalArgumentException(
				"Charge would make totalChargesCents negative: " + amountCents);
		}
		this.totalChargesCents += amountCents;
	}

	/**
	 * Add a prepayment to the running total. Only allowed while OPEN.
	 */
	public void addPrepayment(long amountCents) {
		if (status != BillStatus.OPEN) {
			throw new IllegalStateException("Cannot add prepayment to a settled bill");
		}
		this.prepaymentCents += amountCents;
	}

	/**
	 * Settle the bill: record the amount paid, mark as SETTLED, set timestamp.
	 * Only allowed once.
	 */
	public void settle(long amountCents, Instant settledAt) {
		if (status != BillStatus.OPEN) {
			throw new IllegalStateException("Bill is already settled");
		}
		this.settledAmountCents = amountCents;
		this.status = BillStatus.SETTLED;
		this.settledAt = settledAt;
	}

	// Getters

	public Long getVisitId() {
		return visitId;
	}

	public long getTotalChargesCents() {
		return totalChargesCents;
	}

	public long getPrepaymentCents() {
		return prepaymentCents;
	}

	public long getSettledAmountCents() {
		return settledAmountCents;
	}

	public BillStatus getStatus() {
		return status;
	}

	public Instant getSettledAt() {
		return settledAt;
	}

	public List<Transaction> getTransactions() {
		return Collections.unmodifiableList(transactions);
	}
}
