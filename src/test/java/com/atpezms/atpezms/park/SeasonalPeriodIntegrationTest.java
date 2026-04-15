package com.atpezms.atpezms.park;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atpezms.atpezms.park.repository.SeasonalPeriodRepository;
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
 * Integration test for SeasonalPeriod management endpoints (Phase 2.3).
 *
 * <h2>Seed data</h2>
 * V001 seeds 5 seasonal periods covering the 2026 calendar. Tests that create
 * new periods use 2027+ dates to avoid conflicts with the seeded data.
 *
 * <h2>Overlap logic</h2>
 * The overlap condition is: {@code proposed.start <= existing.end AND proposed.end >= existing.start}.
 * Tests that verify overlap detection create a known period first, then try to
 * create an overlapping one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SeasonalPeriodIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SeasonalPeriodRepository periodRepository;

    // -----------------------------------------------------------------------
    // GET /api/park/seasonal-periods
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnSeededPeriodsOrderedByStartDate() throws Exception {
        // V001 seeds 5 periods for 2026; they should appear in chronological order.
        mockMvc.perform(get("/api/park/seasonal-periods"))
                .andExpect(status().isOk())
                // The DB is seeded; we should get at least one period back.
                // Avoid brittle coupling to the exact seed count.
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
                // First seeded period: 2026-01-01 (OFF_PEAK)
                .andExpect(jsonPath("$[0].startDate").value("2026-01-01"))
                .andExpect(jsonPath("$[0].seasonType").value("OFF_PEAK"));
    }

    // -----------------------------------------------------------------------
    // GET /api/park/seasonal-periods/{id}
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnPeriodById() throws Exception {
        long id = periodRepository.findAllByOrderByStartDateAsc().get(0).getId();

        mockMvc.perform(get("/api/park/seasonal-periods/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void shouldReturn404ForNonExistentPeriodId() throws Exception {
        mockMvc.perform(get("/api/park/seasonal-periods/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEASONAL_PERIOD_NOT_FOUND"));
    }

    // -----------------------------------------------------------------------
    // POST /api/park/seasonal-periods
    // -----------------------------------------------------------------------

    @Test
    void shouldCreateNonOverlappingPeriod() throws Exception {
        // 2027 has no seeded periods
        mockMvc.perform(post("/api/park/seasonal-periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2027-01-01",
                                  "endDate":   "2027-06-30",
                                  "seasonType": "OFF_PEAK"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.startDate").value("2027-01-01"))
                .andExpect(jsonPath("$.seasonType").value("OFF_PEAK"));

        assertThat(periodRepository.findOverlapping(
                java.time.LocalDate.of(2027, 1, 1),
                java.time.LocalDate.of(2027, 6, 30), null)).hasSize(1);
    }

    @Test
    void shouldRejectPeriodThatOverlapsExistingSeededPeriod() throws Exception {
        // 2026-03-01 to 2026-05-15 overlaps:
        //   - the OFF_PEAK period 2026-01-01 to 2026-03-31
        //   - the PEAK period 2026-04-01 to 2026-04-30
        mockMvc.perform(post("/api/park/seasonal-periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-03-01",
                                  "endDate":   "2026-05-15",
                                  "seasonType": "PEAK"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SEASONAL_PERIOD_DATE_CONFLICT"));
    }

    @Test
    void shouldRejectPeriodWhenEndDateBeforeStartDate() throws Exception {
        mockMvc.perform(post("/api/park/seasonal-periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2027-06-30",
                                  "endDate":   "2027-01-01",
                                  "seasonType": "PEAK"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SEASONAL_PERIOD_INVALID_DATES"))
                .andExpect(jsonPath("$.message").value(containsString("2027-01-01")));
    }

    @Test
    void shouldAllowSingleDayPeriod() throws Exception {
        // A period where startDate == endDate is valid (one-day season classification)
        mockMvc.perform(post("/api/park/seasonal-periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2027-08-15",
                                  "endDate":   "2027-08-15",
                                  "seasonType": "PEAK"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value("2027-08-15"))
                .andExpect(jsonPath("$.endDate").value("2027-08-15"));
    }

    // -----------------------------------------------------------------------
    // DELETE /api/park/seasonal-periods/{id}
    // -----------------------------------------------------------------------

    @Test
    void shouldDeleteExistingPeriod() throws Exception {
        // Create a period, then delete it, then confirm it's gone
        String createResult = mockMvc.perform(post("/api/park/seasonal-periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2027-01-01",
                                  "endDate":   "2027-03-31",
                                  "seasonType": "OFF_PEAK"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = ((Number) JsonPath.read(createResult, "$.id")).longValue();

        mockMvc.perform(delete("/api/park/seasonal-periods/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/park/seasonal-periods/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentPeriod() throws Exception {
        mockMvc.perform(delete("/api/park/seasonal-periods/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAllowCreatingNewPeriodAfterDeletingConflicting() throws Exception {
        // Delete a seeded period (e.g. 2026-01-01 to 2026-03-31) then create
        // a new period that overlaps where it was -- this should now succeed.
        long firstPeriodId = periodRepository.findAllByOrderByStartDateAsc().get(0).getId();

        mockMvc.perform(delete("/api/park/seasonal-periods/" + firstPeriodId))
                .andExpect(status().isNoContent());

        // Now we can fill that gap
        mockMvc.perform(post("/api/park/seasonal-periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-01-01",
                                  "endDate":   "2026-03-31",
                                  "seasonType": "PEAK"
                                }
                                """))
                .andExpect(status().isCreated());
    }
}
