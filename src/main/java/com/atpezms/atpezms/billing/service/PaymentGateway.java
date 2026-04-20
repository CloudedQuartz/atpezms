package com.atpezms.atpezms.billing.service;

/**
 * External payment gateway contract.
 *
 * Owned by the Billing context -- other contexts never call this directly.
 * A real implementation would call an external card processor API.
 */
public interface PaymentGateway {
	PaymentResult processPayment(String paymentToken, long amountCents, String currency);
}
