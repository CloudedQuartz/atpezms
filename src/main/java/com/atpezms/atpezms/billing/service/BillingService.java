package com.atpezms.atpezms.billing.service;

import com.atpezms.atpezms.billing.dto.BillResponse;
import com.atpezms.atpezms.billing.dto.RecordTransactionRequest;
import com.atpezms.atpezms.billing.dto.TransactionResponse;
import com.atpezms.atpezms.billing.entity.Bill;
import com.atpezms.atpezms.billing.entity.BillStatus;
import com.atpezms.atpezms.billing.entity.Transaction;
import com.atpezms.atpezms.billing.entity.TransactionType;
import com.atpezms.atpezms.billing.exception.BillAlreadySettledException;
import com.atpezms.atpezms.billing.exception.BillNotFoundException;
import com.atpezms.atpezms.billing.exception.PaymentFailedException;
import com.atpezms.atpezms.billing.repository.BillRepository;
import com.atpezms.atpezms.billing.repository.TransactionRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core billing logic: record transactions, retrieve bills, process checkout.
 *
 * All methods are @Transactional because they involve multiple entity writes
 * (Transaction + Bill update) that must succeed or fail atomically.
 */
@Service
@Transactional(readOnly = true)
public class BillingService {

	private final BillRepository billRepository;
	private final TransactionRepository transactionRepository;
	private final PaymentGateway paymentGateway;
	private final Clock clock;

	public BillingService(BillRepository billRepository,
	                      TransactionRepository transactionRepository,
	                      PaymentGateway paymentGateway,
	                      Clock clock) {
		this.billRepository = billRepository;
		this.transactionRepository = transactionRepository;
		this.paymentGateway = paymentGateway;
		this.clock = clock;
	}

	/**
	 * Record a new charge or prepayment.
	 *
	 * Auto-creates a Bill if one does not exist for the visit.
	 * Uses pessimistic locking to prevent TOCTOU race condition when two
	 * concurrent requests try to create a bill for the same visitId.
	 * Rejects transactions on already-settled bills.
	 */
	@Transactional
	public TransactionResponse recordTransaction(RecordTransactionRequest request) {
		Bill bill = billRepository.findByVisitIdForUpdate(request.visitId())
			.orElseGet(() -> createBillForVisit(request.visitId()));

		if (bill.getStatus() == BillStatus.SETTLED) {
			throw new BillAlreadySettledException(request.visitId());
		}

		String currency = request.currency() != null ? request.currency() : "USD";

		Transaction transaction = new Transaction(
			bill,
			request.type(),
			request.source(),
			request.description(),
			request.amountCents(),
			currency
		);

		// Update the bill's running totals
		if (request.type() == TransactionType.PREPAID_DEPOSIT) {
			bill.addPrepayment(request.amountCents());
		} else {
			bill.addCharge(request.amountCents());
		}

		transactionRepository.save(transaction);
		billRepository.save(bill);

		return TransactionResponse.from(transaction);
	}

	/**
	 * Create a new bill for a visit, handling the race condition where
	 * another thread may have already created it (unique constraint on visit_id).
	 */
	private Bill createBillForVisit(Long visitId) {
		Bill newBill = new Bill(visitId);
		try {
			billRepository.saveAndFlush(newBill);
			return newBill;
		} catch (DataIntegrityViolationException ex) {
			// Another thread won the race — re-lookup with lock
			return billRepository.findByVisitIdForUpdate(visitId)
				.orElseThrow(() -> new IllegalStateException(
					"Bill disappeared after unique constraint violation for visitId=" + visitId));
		}
	}

	/**
	 * Retrieve the bill for a visit.
	 *
	 * Throws BillNotFoundException if no bill exists (visitor hasn't purchased anything yet).
	 */
	public BillResponse getBill(Long visitId) {
		Bill bill = billRepository.findByVisitId(visitId)
			.orElseThrow(() -> new BillNotFoundException(visitId));
		return BillResponse.from(bill);
	}

	/**
	 * Settle the bill for a visit (checkout flow).
	 *
	 * 1. Find bill → throw BillNotFoundException if not found
	 * 2. Check bill is OPEN → throw BillAlreadySettledException if SETTLED
	 * 3. If balance > 0: call PaymentGateway (requires paymentToken)
	 * 4. On gateway failure: throw PaymentFailedException
	 * 5. Record SETTLEMENT transaction
	 * 6. Mark bill SETTLED
	 */
	@Transactional
	public BillResponse settleBill(Long visitId, String paymentToken) {
		Bill bill = billRepository.findByVisitId(visitId)
			.orElseThrow(() -> new BillNotFoundException(visitId));

		if (bill.getStatus() == BillStatus.SETTLED) {
			throw new BillAlreadySettledException(visitId);
		}

		long balance = bill.getBalanceCents();

		long amountPaid;
		if (balance > 0) {
			if (paymentToken == null || paymentToken.isBlank()) {
				throw new PaymentFailedException("Payment token is required when balance is positive");
			}
			PaymentResult result = paymentGateway.processPayment(paymentToken, balance, "USD");
			if (!result.succeeded()) {
				throw new PaymentFailedException(result.errorMessage());
			}
			// Record SETTLEMENT transaction for audit completeness
			Transaction settlement = new Transaction(
				bill,
				TransactionType.CHARGE,
				com.atpezms.atpezms.billing.entity.TransactionSource.TICKET,
				"Settlement payment",
				balance,
				"USD"
			);
			transactionRepository.save(settlement);
			amountPaid = balance;
		} else {
			// Nothing owed (prepayment covers all charges). No settlement transaction needed.
			amountPaid = 0;
		}

		// Settle the bill
		bill.settle(amountPaid, Instant.now(clock));
		billRepository.save(bill);

		return BillResponse.from(bill);
	}
}
