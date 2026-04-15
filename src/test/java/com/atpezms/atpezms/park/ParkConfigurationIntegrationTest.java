package com.atpezms.atpezms.park;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atpezms.atpezms.park.repository.ParkConfigurationRepository;
import com.atpezms.atpezms.ticketing.entity.ParkDayCapacity;
import com.atpezms.atpezms.ticketing.repository.ParkDayCapacityRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for ParkConfiguration management endpoints (Phase 2.2).
 *
 * <h2>Key behaviors verified</h2>
 * <ul>
 *   <li>Creating a new configuration activates it and deactivates the old one.</li>
 *   <li>GET /active always returns the current active configuration.</li>
 *   <li>Capacity increase always succeeds.</li>
 *   <li>Capacity reduction is blocked if a future date has reservations that
 *       would exceed the new limit.</li>
 *   <li>Capacity reduction succeeds when no future date conflicts.</li>
 * </ul>
 *
 * <h2>Cross-context repository access in tests</h2>
 * These tests use {@code ParkDayCapacityRepository} directly to set up
 * capacity reservation data. This is acceptable in tests because the test
 * harness is not a production bounded context -- it is a test fixture that
 * needs direct data control.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ParkConfigurationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ParkConfigurationRepository configRepository;

    @Autowired
    private ParkDayCapacityRepository dayCapacityRepository;

    // -----------------------------------------------------------------------
    // GET /api/park/configurations/active
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnSeededActiveConfiguration() throws Exception {
        // V001 seeds one active configuration with max_daily_capacity=5000
        mockMvc.perform(get("/api/park/configurations/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.maxDailyCapacity").value(5000));
    }

    // -----------------------------------------------------------------------
    // POST /api/park/configurations
    // -----------------------------------------------------------------------

    @Test
    void shouldActivateNewConfigAndDeactivateOld() throws Exception {
        // Sanity check: currently one active config
        assertThat(configRepository.findByActiveTrue()).isPresent();

        mockMvc.perform(post("/api/park/configurations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "maxDailyCapacity": 7000 }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.maxDailyCapacity").value(7000));

        // New config is now active
        mockMvc.perform(get("/api/park/configurations/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxDailyCapacity").value(7000));

        // History has 2 entries; the old one is now inactive
        mockMvc.perform(get("/api/park/configurations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].maxDailyCapacity").value(7000))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].active").value(false));
    }

    @Test
    void shouldAllowCapacityIncreaseUnconditionally() throws Exception {
        // Capacity increase (5000 -> 8000) must always succeed regardless of reservations
        mockMvc.perform(post("/api/park/configurations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "maxDailyCapacity": 8000 }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maxDailyCapacity").value(8000));
    }

    @Test
    void shouldPropagateLargerCapacityToExistingFutureDayRows() throws Exception {
        // Seed a park_day_capacity row for a future date.
        // Use UTC to match the service implementation's time anchor.
        LocalDate futureDate = LocalDate.now(ZoneOffset.UTC).plusDays(5);
        ParkDayCapacity existing = new ParkDayCapacity(futureDate, 5000);
        dayCapacityRepository.save(existing);

        mockMvc.perform(post("/api/park/configurations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "maxDailyCapacity": 7000 }
                                """))
                .andExpect(status().isCreated());

        // The future day row should now have max_capacity updated to 7000
        ParkDayCapacity updated = dayCapacityRepository.findByVisitDate(futureDate).orElseThrow();
        assertThat(updated.getMaxCapacity()).isEqualTo(7000);
    }

    @Test
    void shouldRejectCapacityReductionWhenFutureDayIsOverLimit() throws Exception {
        // Seed a future capacity row that already has more issued slots than
        // the new limit we're about to try setting.
        LocalDate futureDate = LocalDate.now(ZoneOffset.UTC).plusDays(3);

        // Manually insert a park_day_capacity row with issuedCount=200
        // Since there's no API to directly set issuedCount, we save with
        // max=5000 and then increment it using the guarded update 200 times
        // would be slow -- instead we insert via repository with a helper.
        // The only way to set issuedCount directly is through the entity constructor.
        // We'll save it with maxCapacity=5000 and then use the increment query.
        // For the test, we directly construct the row via the repository save,
        // but ParkDayCapacity's constructor doesn't expose issuedCount as a param.
        //
        // Solution: use the incrementIfCapacityAvailable query indirectly by
        // seeding with a very low max (1) and one reservation.
        // Actually: the cleanest approach here is to set maxCapacity=100 via the
        // constructor (which is the initial capacity for this test row), then
        // activate a new config with max=50. That simulates a reduction conflict.
        ParkDayCapacity existing = new ParkDayCapacity(futureDate, 100);
        // Now manually simulate reservations by incrementing through JPA
        // to get issuedCount > 50 (the new proposed max).
        // We save the row first, then update issuedCount via JPQL.
        dayCapacityRepository.save(existing);
        // Use the increment query 60 times to get issuedCount = 60
        // (which is > proposed new max of 50)
        for (int i = 0; i < 60; i++) {
            dayCapacityRepository.incrementIfCapacityAvailable(
                    futureDate, java.time.Instant.now());
        }

        // Now attempt to reduce capacity to 50 (below the 60 already issued)
        mockMvc.perform(post("/api/park/configurations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "maxDailyCapacity": 50 }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CAPACITY_REDUCTION_CONFLICT"));
    }

    @Test
    void shouldAllowCapacityReductionWhenNoFutureDayConflicts() throws Exception {
        // No future park_day_capacity rows exist (they're created on demand
        // during ticket issuance). Reducing capacity is safe.
        mockMvc.perform(post("/api/park/configurations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "maxDailyCapacity": 100 }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maxDailyCapacity").value(100));
    }

    @Test
    void shouldNotCreateNewConfigWhenConflictRejected() throws Exception {
        // Conflict setup: future day with 60 issued > proposed new max of 50
        LocalDate futureDate = LocalDate.now(ZoneOffset.UTC).plusDays(2);
        ParkDayCapacity existing = new ParkDayCapacity(futureDate, 100);
        dayCapacityRepository.save(existing);
        for (int i = 0; i < 60; i++) {
            dayCapacityRepository.incrementIfCapacityAvailable(futureDate, java.time.Instant.now());
        }

        long configsBefore = configRepository.count();

        mockMvc.perform(post("/api/park/configurations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "maxDailyCapacity": 50 }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CAPACITY_REDUCTION_CONFLICT"));

        // The rejection must be atomic: no new configuration row was created
        assertThat(configRepository.count()).isEqualTo(configsBefore);
        // The original config is still active
        assertThat(configRepository.findByActiveTrue()).isPresent();
    }

    @Test
    void shouldIncludeEarliestConflictingDateInErrorMessage() throws Exception {
        // Two conflicting future dates -- error must reference the earlier one
        LocalDate earlier = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        LocalDate later = LocalDate.now(ZoneOffset.UTC).plusDays(5);

        for (LocalDate date : List.of(earlier, later)) {
            ParkDayCapacity row = new ParkDayCapacity(date, 100);
            dayCapacityRepository.save(row);
            for (int i = 0; i < 60; i++) {
                dayCapacityRepository.incrementIfCapacityAvailable(date, java.time.Instant.now());
            }
        }

        mockMvc.perform(post("/api/park/configurations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "maxDailyCapacity": 50 }
                                """))
                .andExpect(status().isUnprocessableEntity())
                // The error message must contain the earlier date string
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(earlier.toString())));
    }
}
