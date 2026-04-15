package com.atpezms.atpezms.park.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atpezms.atpezms.common.entity.SeasonType;
import com.atpezms.atpezms.park.dto.CreateSeasonalPeriodRequest;
import com.atpezms.atpezms.park.dto.SeasonalPeriodResponse;
import com.atpezms.atpezms.park.exception.SeasonalPeriodDateConflictException;
import com.atpezms.atpezms.park.exception.SeasonalPeriodInvalidDatesException;
import com.atpezms.atpezms.park.exception.SeasonalPeriodNotFoundException;
import com.atpezms.atpezms.park.service.SeasonalPeriodService;
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
 * Controller-layer test for {@link SeasonalPeriodController}.
 */
@WebMvcTest(SeasonalPeriodController.class)
@ActiveProfiles("test")
class SeasonalPeriodControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SeasonalPeriodService periodService;

    private static final Instant NOW = Instant.parse("2026-04-15T10:00:00Z");

    private SeasonalPeriodResponse stub(Long id, String start, String end, SeasonType type) {
        return new SeasonalPeriodResponse(id, LocalDate.parse(start), LocalDate.parse(end), type, NOW, NOW);
    }

    // -----------------------------------------------------------------------
    // GET /api/park/seasonal-periods
    // -----------------------------------------------------------------------

    @Test
    void shouldReturn200WithPeriodList() throws Exception {
        when(periodService.listPeriods()).thenReturn(List.of(
                stub(1L, "2026-01-01", "2026-03-31", SeasonType.OFF_PEAK),
                stub(2L, "2026-04-01", "2026-04-30", SeasonType.PEAK)
        ));

        mockMvc.perform(get("/api/park/seasonal-periods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].seasonType").value("OFF_PEAK"))
                .andExpect(jsonPath("$[1].seasonType").value("PEAK"));
    }

    // -----------------------------------------------------------------------
    // GET /api/park/seasonal-periods/{id}
    // -----------------------------------------------------------------------

    @Test
    void shouldReturn200ForExistingPeriod() throws Exception {
        when(periodService.getPeriod(1L)).thenReturn(
                stub(1L, "2026-01-01", "2026-03-31", SeasonType.OFF_PEAK));

        mockMvc.perform(get("/api/park/seasonal-periods/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonType").value("OFF_PEAK"));
    }

    @Test
    void shouldReturn404ForMissingPeriod() throws Exception {
        when(periodService.getPeriod(99L)).thenThrow(new SeasonalPeriodNotFoundException(99L));

        mockMvc.perform(get("/api/park/seasonal-periods/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEASONAL_PERIOD_NOT_FOUND"));
    }

    // -----------------------------------------------------------------------
    // POST /api/park/seasonal-periods
    // -----------------------------------------------------------------------

    @Test
    void shouldReturn201WhenPeriodCreatedSuccessfully() throws Exception {
        when(periodService.createPeriod(any(CreateSeasonalPeriodRequest.class)))
                .thenReturn(stub(6L, "2027-01-01", "2027-03-31", SeasonType.OFF_PEAK));

        mockMvc.perform(post("/api/park/seasonal-periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2027-01-01",
                                  "endDate":   "2027-03-31",
                                  "seasonType": "OFF_PEAK"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seasonType").value("OFF_PEAK"));
    }

    @Test
    void shouldReturn422WhenDatesOverlap() throws Exception {
        when(periodService.createPeriod(any(CreateSeasonalPeriodRequest.class)))
                .thenThrow(new SeasonalPeriodDateConflictException(
                        LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 31)));

        mockMvc.perform(post("/api/park/seasonal-periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2027-01-01",
                                  "endDate":   "2027-03-31",
                                  "seasonType": "PEAK"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SEASONAL_PERIOD_DATE_CONFLICT"));
    }

    @Test
    void shouldReturn422WhenEndDateBeforeStartDate() throws Exception {
        when(periodService.createPeriod(any(CreateSeasonalPeriodRequest.class)))
                .thenThrow(new SeasonalPeriodInvalidDatesException(
                        LocalDate.of(2027, 6, 1), LocalDate.of(2027, 1, 1)));

        mockMvc.perform(post("/api/park/seasonal-periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2027-06-01",
                                  "endDate":   "2027-01-01",
                                  "seasonType": "PEAK"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SEASONAL_PERIOD_INVALID_DATES"));
    }

    @Test
    void shouldReturn400WhenStartDateMissing() throws Exception {
        mockMvc.perform(post("/api/park/seasonal-periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "endDate": "2027-03-31",
                                  "seasonType": "PEAK"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldReturn400WhenSeasonTypeIsInvalid() throws Exception {
        mockMvc.perform(post("/api/park/seasonal-periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2027-01-01",
                                  "endDate":   "2027-03-31",
                                  "seasonType": "INVALID_TYPE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                // Enum coercion fails during JSON deserialization, before Bean Validation.
                // GlobalExceptionHandler maps this to MALFORMED_JSON.
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors.length()").value(0));

        // Proves the failure is at deserialization boundary (controller/service not invoked).
        verifyNoInteractions(periodService);
    }

    // -----------------------------------------------------------------------
    // DELETE /api/park/seasonal-periods/{id}
    // -----------------------------------------------------------------------

    @Test
    void shouldReturn204WhenPeriodDeleted() throws Exception {
        doNothing().when(periodService).deletePeriod(1L);

        mockMvc.perform(delete("/api/park/seasonal-periods/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentPeriod() throws Exception {
        doThrow(new SeasonalPeriodNotFoundException(99L)).when(periodService).deletePeriod(99L);

        mockMvc.perform(delete("/api/park/seasonal-periods/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEASONAL_PERIOD_NOT_FOUND"));
    }
}
