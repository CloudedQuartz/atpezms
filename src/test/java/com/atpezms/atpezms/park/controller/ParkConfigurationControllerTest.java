package com.atpezms.atpezms.park.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atpezms.atpezms.park.dto.CreateParkConfigurationRequest;
import com.atpezms.atpezms.park.dto.ParkConfigurationResponse;
import com.atpezms.atpezms.park.exception.CapacityReductionConflictException;
import com.atpezms.atpezms.park.exception.NoActiveParkConfigurationException;
import com.atpezms.atpezms.park.service.ParkConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-layer test for {@link ParkConfigurationController}.
 */
@WebMvcTest(ParkConfigurationController.class)
@ActiveProfiles("test")
class ParkConfigurationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ParkConfigurationService configService;

    private static final Instant NOW = Instant.parse("2026-04-15T10:00:00Z");

    private ParkConfigurationResponse stub(Long id, boolean active, int maxCapacity) {
        return new ParkConfigurationResponse(id, active, maxCapacity, NOW, NOW);
    }

    // -----------------------------------------------------------------------
    // GET /api/park/configurations
    // -----------------------------------------------------------------------

    @Test
    void shouldReturn200WithConfigurationList() throws Exception {
        when(configService.listConfigurations()).thenReturn(List.of(
                stub(2L, true, 6000),
                stub(1L, false, 5000)
        ));

        mockMvc.perform(get("/api/park/configurations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].maxDailyCapacity").value(6000))
                .andExpect(jsonPath("$[1].active").value(false));
    }

    // -----------------------------------------------------------------------
    // GET /api/park/configurations/active
    // -----------------------------------------------------------------------

    @Test
    void shouldReturn200WithActiveConfiguration() throws Exception {
        when(configService.getActiveConfiguration()).thenReturn(stub(1L, true, 5000));

        mockMvc.perform(get("/api/park/configurations/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.maxDailyCapacity").value(5000));
    }

    @Test
    void shouldReturn404WhenNoActiveConfigurationExists() throws Exception {
        when(configService.getActiveConfiguration())
                .thenThrow(new NoActiveParkConfigurationException());

        mockMvc.perform(get("/api/park/configurations/active"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NO_ACTIVE_PARK_CONFIGURATION"));
    }

    // -----------------------------------------------------------------------
    // POST /api/park/configurations
    // -----------------------------------------------------------------------

    @Test
    void shouldReturn201WhenConfigurationCreated() throws Exception {
        when(configService.createAndActivate(any(CreateParkConfigurationRequest.class)))
                .thenReturn(stub(2L, true, 6000));

        mockMvc.perform(post("/api/park/configurations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "maxDailyCapacity": 6000 }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.maxDailyCapacity").value(6000));
    }

    @Test
    void shouldReturn422WhenCapacityReductionConflicts() throws Exception {
        when(configService.createAndActivate(any(CreateParkConfigurationRequest.class)))
                .thenThrow(new CapacityReductionConflictException(
                        LocalDate.of(2026, 4, 20), 100, 150));

        mockMvc.perform(post("/api/park/configurations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "maxDailyCapacity": 100 }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CAPACITY_REDUCTION_CONFLICT"));
    }

    @Test
    void shouldReturn400WhenMaxDailyCapacityIsMissing() throws Exception {
        mockMvc.perform(post("/api/park/configurations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldReturn400WhenMaxDailyCapacityIsZero() throws Exception {
        mockMvc.perform(post("/api/park/configurations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "maxDailyCapacity": 0 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldReturn400WhenMaxDailyCapacityIsNegative() throws Exception {
        mockMvc.perform(post("/api/park/configurations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "maxDailyCapacity": -1 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
