package com.atpezms.atpezms.ticketing.controller;

import com.atpezms.atpezms.ticketing.dto.IssueVisitRequest;
import com.atpezms.atpezms.ticketing.dto.IssueVisitResponse;
import com.atpezms.atpezms.ticketing.service.VisitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for selling a ticket and starting a visit.
 *
 * <p>Thin controller rule: validate input, delegate to a service, return the
 * correct HTTP status. Domain exceptions bubble up to {@code GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/ticketing/visits")
public class VisitController {
	private final VisitService visitService;

	public VisitController(VisitService visitService) {
		this.visitService = visitService;
	}

	// Requires: ROLE_TICKET_STAFF or ROLE_MANAGER (enforced once Identity slice is built)
	@PostMapping
	public ResponseEntity<IssueVisitResponse> issueTicketAndStartVisit(
			@RequestBody @Valid IssueVisitRequest request
	) {
		IssueVisitResponse response = visitService.issueTicketAndStartVisit(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}
}
