package com.atpezms.atpezms.park;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atpezms.atpezms.park.repository.ZoneRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for Zone CRUD (Phase 2.1).
 *
 * <h2>@SpringBootTest vs @WebMvcTest</h2>
 * These tests boot the full application context (including JPA, Flyway, and the
 * real service layer) and execute requests against an in-memory H2 database seeded
 * by all Flyway migrations. This verifies the full request-to-database round-trip.
 *
 * <h2>@Transactional</h2>
 * Each test runs inside a transaction that is rolled back after the test completes.
 * This means the Flyway-seeded data (5 zones) is always the baseline: zone
 * creations in one test are invisible to other tests and do not accumulate.
 *
 * <h2>Why we test against the seeded zones</h2>
 * V001 seeds 5 zones (ADVENTURE, WATER, FOOD_COURT, EVENTS, ENTRANCE). Tests
 * that verify list behavior can rely on these existing rows instead of creating
 * their own. Tests that verify create/update behavior create new rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ZoneIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ZoneRepository zoneRepository;



    // -----------------------------------------------------------------------
    // GET /api/park/zones
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnSeededZonesIncludingKnownCodesInCodeOrder() throws Exception {
        // V001 seeds 5 zones. We assert the known codes appear in code-alphabetical
        // order without hard-coding the total count, so the test stays valid if
        // future phases add more seed data.
        //
        // Alphabetical order of V001 seed codes: ADVENTURE < ENTRANCE < EVENTS < FOOD_COURT < WATER
        mockMvc.perform(get("/api/park/zones"))
                .andExpect(status().isOk())
                // All known seed codes are present (order-independent check)
                .andExpect(jsonPath("$[*].code", containsInAnyOrder(
                        "ADVENTURE", "WATER", "FOOD_COURT", "EVENTS", "ENTRANCE")))
                // Alphabetical sort: ADVENTURE comes before WATER
                .andExpect(jsonPath("$[0].code").value("ADVENTURE"));
    }

    @Test
    void shouldReturnOnlyActiveZonesWhenActiveOnlyIsTrue() throws Exception {
        // Deactivate one seeded zone, then verify it is excluded from activeOnly=true
        // and still appears in the unrestricted list.
        long adventureId = zoneRepository.findByCode("ADVENTURE").orElseThrow().getId();

        mockMvc.perform(put("/api/park/zones/" + adventureId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Adventure Zone", "active": false }
                                """))
                .andExpect(status().isOk());

        // activeOnly=true must NOT contain ADVENTURE
        mockMvc.perform(get("/api/park/zones").param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", not(hasItem("ADVENTURE"))));

        // Default (activeOnly=false) must still include ADVENTURE
        mockMvc.perform(get("/api/park/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("ADVENTURE")));
    }

    // -----------------------------------------------------------------------
    // GET /api/park/zones/{id}
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnZoneById() throws Exception {
        long adventureId = zoneRepository.findByCode("ADVENTURE").orElseThrow().getId();

        mockMvc.perform(get("/api/park/zones/" + adventureId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADVENTURE"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldReturn404ForNonExistentZoneId() throws Exception {
        mockMvc.perform(get("/api/park/zones/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ZONE_NOT_FOUND"));
    }

    // -----------------------------------------------------------------------
    // POST /api/park/zones
    // -----------------------------------------------------------------------

    @Test
    void shouldCreateNewZoneAndReturnIt() throws Exception {
        mockMvc.perform(post("/api/park/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "VR_ARENA",
                                  "name": "VR Gaming Arena",
                                  "description": "Immersive virtual reality experiences"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.code").value("VR_ARENA"))
                .andExpect(jsonPath("$.name").value("VR Gaming Arena"))
                .andExpect(jsonPath("$.description").value("Immersive virtual reality experiences"))
                .andExpect(jsonPath("$.active").value(true));  // default

        // Verify it's readable from the repository too
        assertThat(zoneRepository.findByCode("VR_ARENA")).isPresent();
    }

    @Test
    void shouldCreateInactiveZoneWhenActiveFalseIsProvided() throws Exception {
        mockMvc.perform(post("/api/park/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "code": "FUTURE_ZONE", "name": "Future Zone", "active": false }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void shouldReturn409WhenCodeIsDuplicate() throws Exception {
        // ADVENTURE already exists (seeded by V001)
        mockMvc.perform(post("/api/park/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "code": "ADVENTURE", "name": "Duplicate Adventure" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ZONE_CODE_ALREADY_EXISTS"));
    }

    // -----------------------------------------------------------------------
    // PUT /api/park/zones/{id}
    // -----------------------------------------------------------------------

    @Test
    void shouldUpdateZoneNameDescriptionAndActive() throws Exception {
        long adventureId = zoneRepository.findByCode("ADVENTURE").orElseThrow().getId();

        mockMvc.perform(put("/api/park/zones/" + adventureId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Adventure Zone (Renovating)",
                                  "description": "Closed for renovation until May 2026",
                                  "active": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADVENTURE"))   // code unchanged
                .andExpect(jsonPath("$.name").value("Adventure Zone (Renovating)"))
                .andExpect(jsonPath("$.description").value("Closed for renovation until May 2026"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void shouldClearDescriptionWhenOmittedFromPut() throws Exception {
        // First create a zone with a description
        String createResult = mockMvc.perform(post("/api/park/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "code": "TEMP_ZONE", "name": "Temp", "description": "Has a description" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Parse the response using JsonPath -- not regex -- so we're resilient to
        // changes in JSON field ordering or formatting.
        long id = ((Number) JsonPath.read(createResult, "$.id")).longValue();

        // PUT without description clears it (PUT = full replacement of mutable fields)
        mockMvc.perform(put("/api/park/zones/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Temp", "active": true }
                                """))
                .andExpect(status().isOk())
                // description should be null (absent / null node), not empty string
                .andExpect(jsonPath("$.description").value(nullValue()));
    }

    @Test
    void shouldReturn404OnUpdateForNonExistentZone() throws Exception {
        mockMvc.perform(put("/api/park/zones/99999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Irrelevant", "active": true }
                                """))
                .andExpect(status().isNotFound());
    }
}
