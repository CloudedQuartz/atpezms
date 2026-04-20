package com.atpezms.atpezms.billing.dto;

import com.atpezms.atpezms.billing.entity.Bill;
import java.time.Instant;

public record BillResponse(
	Long id,
	Long visitId,
	long totalChargesCents,
	long prepaymentCents,
	long settledAmountCents,
	long balanceCents,
	String status,
	Instant settledAt,
	Instant createdAt,
	Instant updatedAt
) {
	public static BillResponse from(Bill bill) {
		return new BillResponse(
			bill.getId(),
			bill.getVisitId(),
			bill.getTotalChargesCents(),
			bill.getPrepaymentCents(),
			bill.getSettledAmountCents(),
			bill.getBalanceCents(),
			bill.getStatus().name(),
			bill.getSettledAt(),
			bill.getCreatedAt(),
			bill.getUpdatedAt()
		);
	}
}
