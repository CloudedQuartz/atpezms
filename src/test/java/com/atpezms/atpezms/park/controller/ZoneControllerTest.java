package com.atpezms.atpezms.park.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atpezms.atpezms.park.dto.CreateZoneRequest;
import com.atpezms.atpezms.park.dto.UpdateZoneRequest;
import com.atpezms.atpezms.park.dto.ZoneResponse;
import com.atpezms.atpezms.park.exception.ZoneCodeAlreadyExistsException;
import com.atpezms.atpezms.park.exception.ZoneNotFoundException;
import com.atpezms.atpezms.park.service.ZoneService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-layer test for {@link ZoneController}.
 *
 * <h2>What this tests</h2>
 * <ul>
 *   <li>Request validation (400 on bad input).</li>
 *   <li>HTTP status codes (200, 201, 404, 409).</li>
 *   <li>Response body shape.</li>
 *   <li>Domain exception → HTTP error code mapping via GlobalExceptionHandler.</li>
 * </ul>
 *
 * <h2>What this does NOT test</h2>
 * Database behavior, transaction semantics, and real service logic.
 * Those are covered by {@link ZoneIntegrationTest}.
 */
@WebMvcTest(ZoneController.class)
@ActiveProfiles("test")
class ZoneControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ZoneService zoneService;

    private static final Instant NOW = Instant.parse("2026-04-15T10:00:00Z");

    private ZoneResponse stubZone(Long id, String code, String name, boolean active) {
        return new ZoneResponse(id, code, name, null, active, NOW, NOW);
    }

    // -----------------------------------------------------------------------
    // GET /api/park/zones
    // -----------------------------------------------------------------------

    @Test
    void shouldReturn200WithListOfZones() throws Exception {
        when(zoneService.listZones(false)).thenReturn(List.of(
                stubZone(1L, "ADVENTURE", "Adventure Zone", true),
                stubZone(2L, "WATER", "Water Zone", true)
        ));

        mockMvc.perform(get("/api/park/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("ADVENTURE"))
                .andExpect(jsonPath("$[1].code").value("WATER"));
    }

    @Test
    void shouldPassActiveOnlyParamToService() throws Exception {
        when(zoneService.listZones(true)).thenReturn(List.of(
                stubZone(1L, "ADVENTURE", "Adventure Zone", true)
        ));

        mockMvc.perform(get("/api/park/zones").param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // -----------------------------------------------------------------------
    // GET /api/park/zones/{id}
    // -----------------------------------------------------------------------

    @Test
    void shouldReturn200WhenZoneExists() throws Exception {
        when(zoneService.getZone(1L)).thenReturn(stubZone(1L, "ADVENTURE", "Adventure Zone", true));

        mockMvc.perform(get("/api/park/zones/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.code").value("ADVENTURE"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldReturn404WhenZoneNotFound() throws Exception {
        when(zoneService.getZone(99L)).thenThrow(new ZoneNotFoundException(99L));

        mockMvc.perform(get("/api/park/zones/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ZONE_NOT_FOUND"));
    }

    // -----------------------------------------------------------------------
    // POST /api/park/zones
    // -----------------------------------------------------------------------

    @Test
    void shouldReturn201WhenZoneCreatedSuccessfully() throws Exception {
        when(zoneService.createZone(any(CreateZoneRequest.class)))
                .thenReturn(stubZone(6L, "VR_ARENA", "VR Arena", true));

        mockMvc.perform(post("/api/park/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "code": "VR_ARENA", "name": "VR Arena" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("VR_ARENA"));
    }

    @Test
    void shouldReturn409WhenCodeAlreadyExists() throws Exception {
        when(zoneService.createZone(any(CreateZoneRequest.class)))
                .thenThrow(new ZoneCodeAlreadyExistsException("ADVENTURE"));

        mockMvc.perform(post("/api/park/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "code": "ADVENTURE", "name": "Duplicate" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ZONE_CODE_ALREADY_EXISTS"));
    }

    @Test
    void shouldReturn400WhenCodeIsBlank() throws Exception {
        mockMvc.perform(post("/api/park/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "code": "", "name": "Missing Code" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldReturn400WhenCodeHasInvalidFormat() throws Exception {
        // codes must match [A-Z0-9_]+; lowercase letters are not allowed
        mockMvc.perform(post("/api/park/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "code": "adventure zone", "name": "Bad Code" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("code"));
    }

    @Test
    void shouldReturn400WhenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/park/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "code": "VALID_CODE", "name": "" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    // -----------------------------------------------------------------------
    // PUT /api/park/zones/{id}
    // -----------------------------------------------------------------------

    @Test
    void shouldReturn200WhenZoneUpdated() throws Exception {
        when(zoneService.updateZone(eq(1L), any(UpdateZoneRequest.class)))
                .thenReturn(stubZone(1L, "ADVENTURE", "Adventure Zone (Updated)", false));

        mockMvc.perform(put("/api/park/zones/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Adventure Zone (Updated)",
                                  "active": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Adventure Zone (Updated)"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void shouldReturn404OnUpdateWhenZoneNotFound() throws Exception {
        when(zoneService.updateZone(eq(99L), any(UpdateZoneRequest.class)))
                .thenThrow(new ZoneNotFoundException(99L));

        mockMvc.perform(put("/api/park/zones/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Irrelevant", "active": true }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ZONE_NOT_FOUND"));
    }

    @Test
    void shouldReturn400WhenUpdateNameIsBlank() throws Exception {
        mockMvc.perform(put("/api/park/zones/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "", "active": true }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldReturn400WhenActiveIsMissingInUpdate() throws Exception {
        // active is @NotNull in UpdateZoneRequest; omitting it is a 400
        mockMvc.perform(put("/api/park/zones/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Something" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
