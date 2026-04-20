package com.atpezms.atpezms.billing.entity;

import com.atpezms.atpezms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Append-only ledger entry. Once created, a Transaction is never modified.
 * This is enforced at three levels:
 * 1. No setters (code level)
 * 2. No PUT/PATCH/DELETE endpoints (API level)
 * 3. Business rule: financial history must be immutable (domain level)
 *
 * Credits (refunds) are expressed as negative CHARGE transactions.
 */
@Entity
@Table(name = "transactions")
public class Transaction extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "bill_id", nullable = false)
	private Bill bill;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 30)
	private TransactionType type;

	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false, length = 30)
	private TransactionSource source;

	@Column(name = "description", nullable = false, length = 500)
	private String description;

	@Column(name = "amount_cents", nullable = false)
	private long amountCents;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "USD";

	protected Transaction() {
	}

	public Transaction(Bill bill, TransactionType type, TransactionSource source,
	                   String description, long amountCents, String currency) {
		if (bill == null) {
			throw new IllegalArgumentException("bill must not be null");
		}
		if (type == null) {
			throw new IllegalArgumentException("type must not be null");
		}
		if (source == null) {
			throw new IllegalArgumentException("source must not be null");
		}
		if (description == null || description.isBlank()) {
			throw new IllegalArgumentException("description must not be blank");
		}
		if (amountCents == 0) {
			throw new IllegalArgumentException("amountCents must not be zero");
		}

		this.bill = bill;
		this.type = type;
		this.source = source;
		this.description = description;
		this.amountCents = amountCents;
		this.currency = currency != null ? currency : "USD";
	}

	// Getters only -- no setters (append-only ledger)

	public Bill getBill() {
		return bill;
	}

	public TransactionType getType() {
		return type;
	}

	public TransactionSource getSource() {
		return source;
	}

	public String getDescription() {
		return description;
	}

	public long getAmountCents() {
		return amountCents;
	}

	public String getCurrency() {
		return currency;
	}
}
