package com.atpezms.atpezms.ticketing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atpezms.atpezms.ticketing.dto.CreateVisitorRequest;
import com.atpezms.atpezms.ticketing.dto.VisitorResponse;
import com.atpezms.atpezms.ticketing.service.VisitorService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-layer test for {@link VisitorController}.
 *
 * <h2>Why @WebMvcTest here, not @SpringBootTest?</h2>
 * {@code @WebMvcTest} is a <em>test slice</em> annotation: it boots only the
 * web layer (controllers, filters, Jackson message converters, the global
 * exception handler) and does <em>not</em> start JPA, Flyway, or the real
 * service beans. The service is replaced by a Mockito mock via
 * {@code @MockitoBean}.
 *
 * This is exactly the right choice here because we want to test two things
 * that belong purely to the controller layer:
 * <ol>
 *   <li><strong>Request validation</strong> -- does the controller return 400
 *       with correct field errors when the request body is invalid?</li>
 *   <li><strong>Response mapping</strong> -- does the controller return 201
 *       with the DTO body when the service returns a valid response?</li>
 * </ol>
 * We do <em>not</em> want to test service logic or database interactions here;
 * those are covered by the integration test below (
 * {@code VisitorIntegrationTest}).
 *
 * <h2>@WebMvcTest and GlobalExceptionHandler</h2>
 * {@code @WebMvcTest} includes all {@code @RestControllerAdvice} beans in the
 * application context, so the {@code GlobalExceptionHandler} is active. This
 * means validation failures are mapped to 400 with a structured body exactly
 * as they would be in production.
 *
 * <h2>@MockitoBean</h2>
 * {@code @MockitoBean} (Spring Boot 4's replacement for the deprecated
 * {@code @MockBean}) registers a Mockito mock as a Spring bean. When the
 * controller calls {@code visitorService.createVisitor(...)}, the mock
 * intercepts the call and returns whatever {@code when(...).thenReturn(...)}
 * specifies.
 *
 * <h2>@ActiveProfiles("test")</h2>
 * Activates {@code application-test.properties}, which provides the
 * {@code atpezms.encryption.key} property. Even though {@code @WebMvcTest}
 * does not start JPA, {@code StringEncryptionConverter} is a {@code @Component}
 * in the common package and will be picked up by the web-layer context scan.
 * Without the key property, the context would fail to start.
 */
@WebMvcTest(VisitorController.class)
@ActiveProfiles("test")
class VisitorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VisitorService visitorService;

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    void shouldReturn201WithBodyWhenRequestIsValid() throws Exception {
        // Arrange: tell the mock service what to return for any valid request.
        VisitorResponse stub = new VisitorResponse(
                1L, "Jane", "Doe", "jane@example.com", null,
                LocalDate.of(1990, 5, 15), 165);
        when(visitorService.createVisitor(any(CreateVisitorRequest.class)))
                .thenReturn(stub);

        // Act + Assert
        mockMvc.perform(post("/api/ticketing/visitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Jane",
                                  "lastName":  "Doe",
                                  "email":     "jane@example.com",
                                  "dateOfBirth": "1990-05-15",
                                  "heightCm":  165
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    // -----------------------------------------------------------------------
    // Validation: required fields
    // -----------------------------------------------------------------------

    @Test
    void shouldReturn400WhenFirstNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/ticketing/visitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "",
                                  "lastName":  "Doe",
                                  "dateOfBirth": "1990-05-15",
                                  "heightCm":  165
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("firstName"));
    }

    @Test
    void shouldReturn400WhenLastNameIsMissing() throws Exception {
        mockMvc.perform(post("/api/ticketing/visitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Jane",
                                  "dateOfBirth": "1990-05-15",
                                  "heightCm":  165
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldReturn400WhenDateOfBirthIsMissing() throws Exception {
        mockMvc.perform(post("/api/ticketing/visitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Jane",
                                  "lastName":  "Doe",
                                  "heightCm":  165
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // -----------------------------------------------------------------------
    // Validation: value bounds
    // -----------------------------------------------------------------------

    @Test
    void shouldReturn400WhenHeightCmIsBelowMinimum() throws Exception {
        mockMvc.perform(post("/api/ticketing/visitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Jane",
                                  "lastName":  "Doe",
                                  "dateOfBirth": "1990-05-15",
                                  "heightCm":  10
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("heightCm"));
    }

    @Test
    void shouldReturn400WhenEmailIsInvalidFormat() throws Exception {
        mockMvc.perform(post("/api/ticketing/visitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Jane",
                                  "lastName":  "Doe",
                                  "email":     "not-an-email",
                                  "dateOfBirth": "1990-05-15",
                                  "heightCm":  165
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    void shouldReturn400WhenDateOfBirthIsInTheFuture() throws Exception {
        // @Past on dateOfBirth must reject future dates.
        mockMvc.perform(post("/api/ticketing/visitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Jane",
                                  "lastName":  "Doe",
                                  "dateOfBirth": "2099-01-01",
                                  "heightCm":  165
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("dateOfBirth"));
    }

    // -----------------------------------------------------------------------
    // Malformed body
    // -----------------------------------------------------------------------

    @Test
    void shouldReturn400WhenBodyIsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/ticketing/visitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }
}
