package com.atpezms.atpezms.billing.service;

/**
 * Result of a payment gateway call.
 */
public record PaymentResult(boolean succeeded, String errorMessage) {
	public static PaymentResult success() {
		return new PaymentResult(true, null);
	}

	public static PaymentResult failure(String errorMessage) {
		return new PaymentResult(false, errorMessage);
	}
}
