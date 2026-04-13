package com.atpezms.atpezms.ticketing.controller;

import com.atpezms.atpezms.ticketing.dto.CreateVisitorRequest;
import com.atpezms.atpezms.ticketing.dto.VisitorResponse;
import com.atpezms.atpezms.ticketing.service.VisitorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the visitors resource.
 *
 * <h2>Thin controller rule</h2>
 * This controller does exactly three things:
 * <ol>
 *   <li>Accept the HTTP request and deserialize the JSON body into a DTO.</li>
 *   <li>Trigger Bean Validation via {@code @Valid}.</li>
 *   <li>Delegate to the service and return the result with the correct HTTP
 *       status code.</li>
 * </ol>
 * No business logic lives here. If the service throws a domain exception,
 * the {@code GlobalExceptionHandler} catches it and converts it to a
 * structured error response. The controller never catches exceptions itself.
 *
 * <h2>Why 201 Created, not 200 OK?</h2>
 * HTTP semantics: 201 means "the request succeeded and a new resource was
 * created". Returning 200 for a POST that creates a resource is technically
 * correct but misleading -- 201 signals to API clients that they can find the
 * new resource at a specific location. We use {@code ResponseEntity.status(201)}
 * to set the status explicitly rather than relying on a default.
 *
 * A {@code Location} header pointing to {@code /api/ticketing/visitors/{id}}
 * would be RESTfully ideal here, but is deferred until the GET-by-ID endpoint
 * is built (Phase 1.3 or later).
 */
@RestController
@RequestMapping("/api/ticketing/visitors")
public class VisitorController {

    private final VisitorService visitorService;

    public VisitorController(VisitorService visitorService) {
        this.visitorService = visitorService;
    }

    /**
     * Registers a new visitor.
     *
     * <p>{@code POST /api/ticketing/visitors}
     *
     * <p>Requires: {@code ROLE_TICKET_STAFF} or {@code ROLE_MANAGER}
     * (enforced once the Identity/Security slice is built; currently open).
     *
     * @param request the validated registration body
     * @return 201 with the saved visitor DTO
     */
    // Requires: ROLE_TICKET_STAFF or ROLE_MANAGER
    @PostMapping
    public ResponseEntity<VisitorResponse> createVisitor(
            @RequestBody @Valid CreateVisitorRequest request) {
        VisitorResponse response = visitorService.createVisitor(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
