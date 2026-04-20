package com.atpezms.atpezms.billing.controller;

import com.atpezms.atpezms.billing.dto.BillResponse;
import com.atpezms.atpezms.billing.dto.RecordTransactionRequest;
import com.atpezms.atpezms.billing.dto.SettleBillRequest;
import com.atpezms.atpezms.billing.dto.TransactionResponse;
import com.atpezms.atpezms.billing.service.BillingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin controller for billing operations.
 *
 * All business logic is delegated to BillingService.
 *
 * Note: @PreAuthorize annotations will be added when Phase 3.2 (Security) is implemented.
 * Endpoints will require ROLE_TICKET_STAFF or ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/billing")
@Validated
public class BillingController {

	private final BillingService billingService;

	public BillingController(BillingService billingService) {
		this.billingService = billingService;
	}

	/**
	 * Record a new charge or prepayment.
	 * Auto-creates a Bill if one does not exist for the visit.
	 */
	@PostMapping("/transactions")
	public ResponseEntity<TransactionResponse> recordTransaction(
			@RequestBody @Valid RecordTransactionRequest request) {
		TransactionResponse response = billingService.recordTransaction(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	/**
	 * Retrieve the bill for a visit.
	 * 404 if no bill exists for the visit.
	 */
	@GetMapping("/visits/{visitId}/bill")
	public ResponseEntity<BillResponse> getBill(@PathVariable Long visitId) {
		BillResponse response = billingService.getBill(visitId);
		return ResponseEntity.ok(response);
	}

	/**
	 * Settle the bill for a visit (checkout).
	 * Calls payment gateway if balance > 0.
	 */
	@PostMapping("/visits/{visitId}/checkout")
	public ResponseEntity<BillResponse> checkout(
			@PathVariable Long visitId,
			@RequestBody @Valid SettleBillRequest request) {
		BillResponse response = billingService.settleBill(visitId, request.paymentToken());
		return ResponseEntity.ok(response);
	}
}
