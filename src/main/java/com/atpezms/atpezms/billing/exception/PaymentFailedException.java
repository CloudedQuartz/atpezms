package com.atpezms.atpezms.billing.exception;

import com.atpezms.atpezms.common.exception.BusinessRuleViolationException;

public class PaymentFailedException extends BusinessRuleViolationException {
	public PaymentFailedException(String message) {
		super("PAYMENT_FAILED", "Payment failed: " + message);
	}
}
