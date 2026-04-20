package com.atpezms.atpezms.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atpezms.atpezms.billing.repository.BillRepository;
import com.atpezms.atpezms.billing.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for Billing (Phase 4).
 *
 * <h2>@SpringBootTest vs @WebMvcTest</h2>
 * These tests boot the full application context (including JPA, Flyway, and the
 * real service layer) and execute requests against an in-memory H2 database.
 * This verifies the full request-to-database round-trip.
 *
 * <h2>@Transactional</h2>
 * Each test runs inside a transaction that is rolled back after the test completes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BillingIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private BillRepository billRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	// -----------------------------------------------------------------------
	// Record transaction + auto-create bill
	// -----------------------------------------------------------------------

	@Test
	void shouldAutoCreateBillOnFirstTransaction() throws Exception {
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
			.andExpect(jsonPath("$.amountCents").value(500));

		// Verify bill was auto-created
		assertThat(billRepository.findByVisitId(1L)).isPresent();
	}

	@Test
	void shouldRecordMultipleTransactionsAndAccumulateTotals() throws Exception {
		// Record a prepayment
		mockMvc.perform(post("/api/billing/transactions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "visitId": 2,
					  "type": "PREPAID_DEPOSIT",
					  "source": "TICKET",
					  "description": "Ticket prepayment",
					  "amountCents": 1000
					}
					"""))
			.andExpect(status().isCreated());

		// Record a charge
		mockMvc.perform(post("/api/billing/transactions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "visitId": 2,
					  "type": "CHARGE",
					  "source": "FOOD",
					  "description": "Pizza",
					  "amountCents": 800
					}
					"""))
			.andExpect(status().isCreated());

		// Verify bill totals
		mockMvc.perform(get("/api/billing/visits/2/bill"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalChargesCents").value(800))
			.andExpect(jsonPath("$.prepaymentCents").value(1000))
			.andExpect(jsonPath("$.balanceCents").value(-200)); // overpaid
	}

	// -----------------------------------------------------------------------
	// Get bill
	// -----------------------------------------------------------------------

	@Test
	void shouldReturn404WhenNoBillExists() throws Exception {
		mockMvc.perform(get("/api/billing/visits/99999/bill"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BILL_NOT_FOUND"));
	}

	@Test
	void shouldReturnBillWithComputedBalance() throws Exception {
		// Set up: record charges and prepayment
		mockMvc.perform(post("/api/billing/transactions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "visitId": 3,
					  "type": "CHARGE",
					  "source": "FOOD",
					  "description": "Meal",
					  "amountCents": 1500
					}
					"""))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/billing/transactions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "visitId": 3,
					  "type": "PREPAID_DEPOSIT",
					  "source": "TICKET",
					  "description": "Deposit",
					  "amountCents": 500
					}
					"""))
			.andExpect(status().isCreated());

		// Verify balance = 1500 - 500 = 1000
		mockMvc.perform(get("/api/billing/visits/3/bill"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalChargesCents").value(1500))
			.andExpect(jsonPath("$.prepaymentCents").value(500))
			.andExpect(jsonPath("$.balanceCents").value(1000))
			.andExpect(jsonPath("$.status").value("OPEN"));
	}

	// -----------------------------------------------------------------------
	// Checkout
	// -----------------------------------------------------------------------

	@Test
	void shouldSettleBillOnCheckout() throws Exception {
		// Set up: record a charge
		mockMvc.perform(post("/api/billing/transactions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "visitId": 4,
					  "type": "CHARGE",
					  "source": "MERCHANDISE",
					  "description": "T-shirt",
					  "amountCents": 2000
					}
					"""))
			.andExpect(status().isCreated());

		// Checkout
		mockMvc.perform(post("/api/billing/visits/4/checkout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "paymentToken": "tok_visa_mock_1234" }
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SETTLED"))
			.andExpect(jsonPath("$.balanceCents").value(0))
			.andExpect(jsonPath("$.settledAmountCents").value(2000));

		// Verify bill is settled in DB
		assertThat(billRepository.findByVisitId(4L).orElseThrow().getStatus().name())
			.isEqualTo("SETTLED");
	}

	@Test
	void shouldNotRecordTransactionOnSettledBill() throws Exception {
		// Set up and checkout
		mockMvc.perform(post("/api/billing/transactions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "visitId": 5,
					  "type": "CHARGE",
					  "source": "EVENT",
					  "description": "Show ticket",
					  "amountCents": 300
					}
					"""))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/billing/visits/5/checkout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "paymentToken": "tok_visa_mock_1234" }
					"""))
			.andExpect(status().isOk());

		// Try to record another transaction → 422
		mockMvc.perform(post("/api/billing/transactions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "visitId": 5,
					  "type": "CHARGE",
					  "source": "FOOD",
					  "description": "After checkout charge",
					  "amountCents": 100
					}
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("BILL_ALREADY_SETTLED"));
	}

	@Test
	void shouldSettleWithoutPaymentWhenBalanceIsZeroOrNegative() throws Exception {
		// Prepayment covers all charges
		mockMvc.perform(post("/api/billing/transactions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "visitId": 6,
					  "type": "PREPAID_DEPOSIT",
					  "source": "TICKET",
					  "description": "Full prepayment",
					  "amountCents": 5000
					}
					"""))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/billing/transactions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "visitId": 6,
					  "type": "CHARGE",
					  "source": "FOOD",
					  "description": "Expensive meal",
					  "amountCents": 3000
					}
					"""))
			.andExpect(status().isCreated());

		// Balance = 3000 - 5000 = -2000 (overpaid). Checkout should succeed without payment token.
		mockMvc.perform(post("/api/billing/visits/6/checkout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SETTLED"));
	}

	@Test
	void shouldRecordSettlementTransactionAfterCheckout() throws Exception {
		// Set up: record a charge
		mockMvc.perform(post("/api/billing/transactions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "visitId": 7,
					  "type": "CHARGE",
					  "source": "FOOD",
					  "description": "Lunch",
					  "amountCents": 1200
					}
					"""))
			.andExpect(status().isCreated());

		// Checkout
		mockMvc.perform(post("/api/billing/visits/7/checkout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "paymentToken": "tok_visa_mock_1234" }
					"""))
			.andExpect(status().isOk());

		// Verify settlement transaction exists in DB
		long billId = billRepository.findByVisitId(7L).orElseThrow().getId();
		var transactions = transactionRepository.findByBillIdOrderByCreatedAtAsc(billId);
		assertThat(transactions).hasSize(2);
		assertThat(transactions.get(1).getDescription()).isEqualTo("Settlement payment");
		assertThat(transactions.get(1).getAmountCents()).isEqualTo(1200);
	}
}
