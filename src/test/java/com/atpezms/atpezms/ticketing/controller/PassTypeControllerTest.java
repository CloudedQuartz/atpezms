package com.atpezms.atpezms.ticketing.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test for GET /api/ticketing/pass-types.
 *
 * Why @SpringBootTest + @AutoConfigureMockMvc rather than @WebMvcTest here:
 *
 * @WebMvcTest slices the context to the web layer only (controllers, filters,
 * message converters) and mocks everything else. That is the right choice when
 * testing controller mapping, validation, and error handling in isolation with
 * a mocked service.
 *
 * Here we want to verify the full read path end-to-end: Flyway seeds the
 * pass types, the repository fetches them from H2, the service maps them to
 * DTOs, and the controller serializes them. @SpringBootTest boots the full
 * context (JPA, Flyway, real beans). @AutoConfigureMockMvc wires a MockMvc
 * instance that exercises the full DispatcherServlet pipeline without binding
 * to a real TCP port -- fast and hermetic.
 *
 * @ActiveProfiles("test") activates the in-memory H2 configuration from
 * application-test.properties so this test never touches the dev file database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PassTypeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void shouldReturnAllSeededActivePassTypes() throws Exception {
		mockMvc.perform(get("/api/ticketing/pass-types"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(5)))
				.andExpect(jsonPath(
						"$[*].code",
						containsInAnyOrder(
								"SINGLE_DAY", "MULTI_DAY", "RIDE_SPECIFIC", "FAMILY", "FAST_TRACK")));
	}
}
