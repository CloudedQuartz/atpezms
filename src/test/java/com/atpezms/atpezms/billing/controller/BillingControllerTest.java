package com.atpezms.atpezms.billing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atpezms.atpezms.billing.dto.BillResponse;
import com.atpezms.atpezms.billing.dto.RecordTransactionRequest;
import com.atpezms.atpezms.billing.dto.TransactionResponse;
import com.atpezms.atpezms.billing.exception.BillAlreadySettledException;
import com.atpezms.atpezms.billing.exception.BillNotFoundException;
import com.atpezms.atpezms.billing.exception.PaymentFailedException;
import com.atpezms.atpezms.billing.service.BillingService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-layer test for {@link BillingController}.
 *
 * <h2>What this tests</h2>
 * <ul>
 *   <li>Request validation (400 on bad input).</li>
 *   <li>HTTP status codes (201, 200, 404, 422).</li>
 *   <li>Response body shape.</li>
 *   <li>Domain exception → HTTP error code mapping via GlobalExceptionHandler.</li>
 * </ul>
 *
 * <h2>What this does NOT test</h2>
 * Database behavior, transaction semantics, and real service logic.
 * Those are covered by {@link BillingIntegrationTest}.
 */
@WebMvcTest(BillingController.class)
@ActiveProfiles("test")
class BillingControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BillingService billingService;

	private static final Instant NOW = Instant.parse("2026-04-15T10:00:00Z");

	// -----------------------------------------------------------------------
	// POST /api/billing/transactions
	// -----------------------------------------------------------------------

	@Test
	void shouldReturn201WhenTransactionRecorded() throws Exception {
		when(billingService.recordTransaction(any(RecordTransactionRequest.class)))
			.thenReturn(new TransactionResponse(
				1L, 1L, "CHARGE", "FOOD", "Burger at Food Court", 500, "USD", NOW));

		mockMvc.perform(post("/api/billing/transactions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "visitId": 1,
					  "type": "CHARGE",
					  "source": "FOOD",
					  "description": "Burger at Food Court",
					  "amountCents": 500
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(1))
			.andExpect(jsonPath("$.type").value("CHARGE"))
			.andExpect(jsonPath("$.amountCents").value(500));
	}

	@Test
	void shouldReturn400WhenVisitIdIsMissing() throws Exception {
		mockMvc.perform(post("/api/billing/transactions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "type": "CHARGE",
					  "source": "FOOD",
					  "description": "Burger",
					  "amountCents": 500
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void shouldReturn400WhenAmountCentsIsZero() throws Exception {
		mockMvc.perform(post("/api/billing/transactions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "visitId": 1,
					  "type": "CHARGE",
					  "source": "FOOD",
					  "description": "Free item",
					  "amountCents": 0
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void shouldReturn400WhenDescriptionIsBlank() throws Exception {
		mockMvc.perform(post("/api/billing/transactions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "visitId": 1,
					  "type": "CHARGE",
					  "source": "FOOD",
					  "description": "",
					  "amountCents": 500
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void shouldReturn422WhenTransactionOnSettledBill() throws Exception {
		when(billingService.recordTransaction(any(RecordTransactionRequest.class)))
			.thenThrow(new BillAlreadySettledException(1L));

		mockMvc.perform(post("/api/billing/transactions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "visitId": 1,
					  "type": "CHARGE",
					  "source": "FOOD",
					  "description": "Burger",
					  "amountCents": 500
					}
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("BILL_ALREADY_SETTLED"));
	}

	// -----------------------------------------------------------------------
	// GET /api/billing/visits/{visitId}/bill
	// -----------------------------------------------------------------------

	@Test
	void shouldReturn200WithBill() throws Exception {
		when(billingService.getBill(1L))
			.thenReturn(new BillResponse(1L, 1L, 1500, 500, 0, 1000, "OPEN", null, NOW, NOW));

		mockMvc.perform(get("/api/billing/visits/1/bill"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(1))
			.andExpect(jsonPath("$.visitId").value(1))
			.andExpect(jsonPath("$.totalChargesCents").value(1500))
			.andExpect(jsonPath("$.prepaymentCents").value(500))
			.andExpect(jsonPath("$.balanceCents").value(1000))
			.andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	void shouldReturn404WhenBillNotFound() throws Exception {
		when(billingService.getBill(99L))
			.thenThrow(new BillNotFoundException(99L));

		mockMvc.perform(get("/api/billing/visits/99/bill"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BILL_NOT_FOUND"));
	}

	// -----------------------------------------------------------------------
	// POST /api/billing/visits/{visitId}/checkout
	// -----------------------------------------------------------------------

	@Test
	void shouldReturn200WhenCheckoutSucceeds() throws Exception {
		when(billingService.settleBill(eq(1L), eq("tok_visa_mock_1234")))
			.thenReturn(new BillResponse(1L, 1L, 1500, 500, 1000, 0, "SETTLED", NOW, NOW, NOW));

		mockMvc.perform(post("/api/billing/visits/1/checkout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "paymentToken": "tok_visa_mock_1234" }
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SETTLED"))
			.andExpect(jsonPath("$.balanceCents").value(0));
	}

	@Test
	void shouldReturn422WhenPaymentFails() throws Exception {
		when(billingService.settleBill(eq(1L), eq("tok_declined")))
			.thenThrow(new PaymentFailedException("Card declined"));

		mockMvc.perform(post("/api/billing/visits/1/checkout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "paymentToken": "tok_declined" }
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("PAYMENT_FAILED"));
	}

	@Test
	void shouldReturn422WhenCheckoutOnAlreadySettledBill() throws Exception {
		when(billingService.settleBill(eq(1L), any()))
			.thenThrow(new BillAlreadySettledException(1L));

		mockMvc.perform(post("/api/billing/visits/1/checkout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "paymentToken": "tok_visa_mock_1234" }
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("BILL_ALREADY_SETTLED"));
	}
}
