package com.atpezms.atpezms.billing.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Mock payment gateway that always succeeds.
 *
 * Active in all profiles except "prod" to prevent accidental
 * use of the mock in production.
 */
@Service
@Profile("!real-integrations")
public class MockPaymentGateway implements PaymentGateway {

	@Override
	public PaymentResult processPayment(String paymentToken, long amountCents, String currency) {
		return PaymentResult.success();
	}
}
