package com.atpezms.atpezms.billing.dto;

import com.atpezms.atpezms.billing.entity.Transaction;
import java.time.Instant;

public record TransactionResponse(
	Long id,
	Long billId,
	String type,
	String source,
	String description,
	long amountCents,
	String currency,
	Instant createdAt
) {
	public static TransactionResponse from(Transaction t) {
		return new TransactionResponse(
			t.getId(),
			t.getBill().getId(),
			t.getType().name(),
			t.getSource().name(),
			t.getDescription(),
			t.getAmountCents(),
			t.getCurrency(),
			t.getCreatedAt()
		);
	}
}
