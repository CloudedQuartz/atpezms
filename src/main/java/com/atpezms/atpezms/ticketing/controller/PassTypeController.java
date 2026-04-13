package com.atpezms.atpezms.ticketing.controller;

import com.atpezms.atpezms.ticketing.dto.PassTypeResponse;
import com.atpezms.atpezms.ticketing.service.PassTypeService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the pass-types resource.
 *
 * {@code @RestController} combines {@code @Controller} and
 * {@code @ResponseBody}: every method return value is serialized directly to
 * the HTTP response body (as JSON via Jackson) rather than being treated as a
 * view name.
 *
 * {@code @RequestMapping} at the class level sets the base path for all methods
 * in this controller. The path follows the convention from IMPLEMENTATION.md:
 * /api/{@code <context>}/{@code <resource>}.
 *
 * The controller is intentionally thin -- it only accepts the request, delegates
 * to the service, and returns the result. No business logic lives here.
 */
@RestController
@RequestMapping("/api/ticketing/pass-types")
public class PassTypeController {
	private final PassTypeService passTypeService;

	public PassTypeController(PassTypeService passTypeService) {
		this.passTypeService = passTypeService;
	}

	// Requires: ROLE_TICKET_STAFF or ROLE_MANAGER (enforced once Identity slice is built)
	@GetMapping
	public List<PassTypeResponse> listPassTypes() {
		return passTypeService.listActivePassTypes();
	}
}
