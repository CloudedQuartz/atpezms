package com.atpezms.atpezms.ticketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atpezms.atpezms.ticketing.repository.VisitorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for the visitor registration flow.
 *
 * <h2>Why @SpringBootTest here?</h2>
 * These tests verify the full stack end-to-end:
 * <ul>
 *   <li>The HTTP request is deserialized by Jackson.</li>
 *   <li>Bean Validation fires on the DTO.</li>
 *   <li>The controller delegates to the real {@code VisitorService}.</li>
 *   <li>The service constructs a {@code Visitor} entity and calls
 *       {@code VisitorRepository.save()}.</li>
 *   <li>Hibernate encrypts the PII fields via
 *       {@code StringEncryptionConverter} and writes them to the in-memory
 *       H2 database.</li>
 *   <li>The saved entity is mapped to a {@code VisitorResponse} and returned
 *       as JSON.</li>
 * </ul>
 * A {@code @WebMvcTest} slice cannot cover this path because it does not boot
 * JPA or Flyway. Only {@code @SpringBootTest} gives us the full context.
 *
 * <h2>@Transactional on the test class</h2>
 * Annotating the test class with {@code @Transactional} wraps each test method
 * in a transaction that is rolled back after the method completes. This means:
 * <ul>
 *   <li>Tests are isolated -- data written in one test is never visible to
 *       another.</li>
 *   <li>No cleanup code is needed -- the rollback restores the database to its
 *       Flyway-seeded baseline automatically.</li>
 * </ul>
 * Important: rollback works because both the test and the service share the
 * same transaction (Spring propagates the test transaction into the service
 * method). If a service method were annotated with
 * {@code @Transactional(propagation = REQUIRES_NEW)}, it would commit
 * independently and not be rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VisitorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VisitorRepository visitorRepository;

    // -----------------------------------------------------------------------
    // Happy path: successful registration
    // -----------------------------------------------------------------------

    @Test
    void shouldCreateVisitorAndReturnDecryptedPiiInResponse() throws Exception {
        mockMvc.perform(post("/api/ticketing/visitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":   "Amara",
                                  "lastName":    "Perera",
                                  "email":       "amara@example.com",
                                  "phone":       "+94 77 123 4567",
                                  "dateOfBirth": "1988-03-22",
                                  "heightCm":    172
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firstName").value("Amara"))
                .andExpect(jsonPath("$.lastName").value("Perera"))
                .andExpect(jsonPath("$.email").value("amara@example.com"))
                .andExpect(jsonPath("$.phone").value("+94 77 123 4567"))
                .andExpect(jsonPath("$.dateOfBirth").value("1988-03-22"))
                .andExpect(jsonPath("$.heightCm").value(172));
    }

    @Test
    void shouldRoundTripPiiThroughEncryptionConverter() throws Exception {
        // This test verifies that the AES-GCM encryption round-trip works
        // end-to-end through the full JPA stack:
        //   1. A visitor is saved: Hibernate calls StringEncryptionConverter
        //      to encrypt PII fields before the INSERT.
        //   2. The entity is reloaded from the repository: Hibernate calls
        //      the converter again to decrypt the columns on SELECT.
        //   3. The Java-side getters return the original plaintext.
        //
        // What this test does NOT verify directly: that the raw database
        // column contains ciphertext (not plaintext). That would require a
        // native JDBC query to read the column value before decryption.
        // The StringEncryptionConverterTest (unit test) already verifies the
        // encrypt/decrypt logic in isolation. Together, the two tests give
        // strong confidence in the mechanism.
        String responseBody = mockMvc.perform(post("/api/ticketing/visitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":   "Kasun",
                                  "lastName":    "Silva",
                                  "email":       "kasun@example.com",
                                  "dateOfBirth": "1995-07-10",
                                  "heightCm":    180
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract the generated ID from the response JSON.
        long id = Long.parseLong(
                responseBody.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // Load via repository (triggers a SELECT + decryption by the converter).
        var saved = visitorRepository.findById(id).orElseThrow();
        assertThat(saved.getFirstName()).isEqualTo("Kasun");
        assertThat(saved.getLastName()).isEqualTo("Silva");
        assertThat(saved.getEmail()).isEqualTo("kasun@example.com");
        assertThat(saved.getPhone()).isNull();   // not provided in request
        assertThat(saved.getHeightCm()).isEqualTo(180);
    }

    @Test
    void shouldAllowRegistrationWithoutOptionalFields() throws Exception {
        // email and phone are optional. A visitor with only required fields
        // must be saved successfully.
        mockMvc.perform(post("/api/ticketing/visitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":   "Nimal",
                                  "lastName":    "Fernando",
                                  "dateOfBirth": "2000-01-01",
                                  "heightCm":    160
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(nullValue()))
                .andExpect(jsonPath("$.phone").value(nullValue()));
    }
}
