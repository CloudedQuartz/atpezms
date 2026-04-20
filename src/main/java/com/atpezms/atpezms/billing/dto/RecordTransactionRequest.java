package com.atpezms.atpezms.billing.dto;

import com.atpezms.atpezms.billing.entity.TransactionSource;
import com.atpezms.atpezms.billing.entity.TransactionType;
import com.atpezms.atpezms.billing.validation.NonZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RecordTransactionRequest(
	@NotNull @Positive Long visitId,
	@NotNull TransactionType type,
	@NotNull TransactionSource source,
	@NotBlank @Size(max = 500) String description,
	@NotNull @NonZero Long amountCents,
	String currency
) {}
