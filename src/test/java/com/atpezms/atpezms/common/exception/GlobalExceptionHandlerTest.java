package com.atpezms.atpezms.common.exception;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integration test for the GlobalExceptionHandler.
 *
 * We use MockMvcBuilders.standaloneSetup() to test the exception handler without
 * booting the full Spring context. This wires the Spring MVC request routing,
 * validation, and exception resolution pipeline exactly as it runs in production,
 * but keeps the test lightning fast.
 */
class GlobalExceptionHandlerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new DummyController())
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void shouldMapBaseExceptionToConfiguredStatusAndCode() throws Exception {
		mockMvc.perform(post("/dummy/throw-base"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.code").value("VISITOR_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Visitor does not exist"))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.fieldErrors").isEmpty());
	}

	@Test
	void shouldMapValidationExceptionTo400WithFieldErrors() throws Exception {
		String invalidJson = """
				{ "name": "" }
				""";

		mockMvc.perform(post("/dummy/validate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidJson))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.message").value("Request validation failed"))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.fieldErrors", hasSize(1)))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("must not be blank"));
	}

	@Test
	void shouldMapHttpMessageNotReadableExceptionTo400() throws Exception {
		String malformedJson = """
				{ "name": "test", }
				"""; // trailing comma is malformed

		mockMvc.perform(post("/dummy/validate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(malformedJson))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
				.andExpect(jsonPath("$.message").value("Malformed request body"));
	}

	@Test
	void shouldMapStateConflictExceptionTo409() throws Exception {
		mockMvc.perform(post("/dummy/throw-conflict"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.code").value("WRISTBAND_ALREADY_ACTIVE"))
				.andExpect(jsonPath("$.message").value("Wristband is already active"))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.fieldErrors").isEmpty());
	}

	@Test
	void shouldMapUnexpectedExceptionTo500WithoutLeakingMessage() throws Exception {
		// The handler must return 500 + INTERNAL_ERROR code without exposing the
		// internal exception message to the client (security: no information leak).
		mockMvc.perform(post("/dummy/throw-unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.message").value("Unexpected server error"))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.fieldErrors").isEmpty());
	}

	@RestController
	static class DummyController {
		@PostMapping("/dummy/throw-base")
		void throwBase() {
			throw new ResourceNotFoundException("VISITOR_NOT_FOUND", "Visitor does not exist");
		}

		@PostMapping("/dummy/throw-conflict")
		void throwConflict() {
			throw new StateConflictException("WRISTBAND_ALREADY_ACTIVE", "Wristband is already active");
		}

		@PostMapping("/dummy/throw-unexpected")
		void throwUnexpected() {
			throw new RuntimeException("internal details that must not reach the client");
		}

		@PostMapping("/dummy/validate")
		void validate(@Valid @RequestBody DummyRequest request) {
			// Body empty, we only care about the @Valid failure
		}
	}

	record DummyRequest(@NotBlank String name) {}
}
