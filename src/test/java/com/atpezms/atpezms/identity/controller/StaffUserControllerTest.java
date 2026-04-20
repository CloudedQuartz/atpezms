package com.atpezms.atpezms.identity.controller;

import com.atpezms.atpezms.identity.dto.CreateStaffUserRequest;
import com.atpezms.atpezms.identity.dto.StaffUserResponse;
import com.atpezms.atpezms.identity.dto.UpdateStaffUserRolesRequest;
import com.atpezms.atpezms.identity.entity.Role;
import com.atpezms.atpezms.identity.exception.StaffUserNotFoundException;
import com.atpezms.atpezms.identity.exception.UsernameAlreadyExistsException;
import com.atpezms.atpezms.identity.service.StaffUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StaffUserController.class)
class StaffUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private StaffUserService staffUserService;

    @Test
    void listUsers_Returns200() throws Exception {
        StaffUserResponse response = new StaffUserResponse(
            1L, "admin", "System Administrator", true, List.of("ROLE_ADMIN"), Instant.now(), Instant.now()
        );
        when(staffUserService.listUsers()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/identity/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username").value("admin"));
    }

    @Test
    void getUser_Success_Returns200() throws Exception {
        StaffUserResponse response = new StaffUserResponse(
            1L, "admin", "System Administrator", true, List.of("ROLE_ADMIN"), Instant.now(), Instant.now()
        );
        when(staffUserService.getUser(1L)).thenReturn(response);

        mockMvc.perform(get("/api/identity/users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    void createUser_Success_Returns201() throws Exception {
        CreateStaffUserRequest request = new CreateStaffUserRequest(
            "jdoe", "password123", "John Doe", Set.of(Role.ROLE_TICKET_STAFF)
        );
        StaffUserResponse response = new StaffUserResponse(
            2L, "jdoe", "John Doe", true, List.of("ROLE_TICKET_STAFF"), Instant.now(), Instant.now()
        );

        when(staffUserService.createUser(any(CreateStaffUserRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/identity/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("jdoe"));
    }

    @Test
    void createUser_DuplicateUsername_Returns409() throws Exception {
        CreateStaffUserRequest request = new CreateStaffUserRequest(
            "admin", "password123", "Admin Duplicate", Set.of(Role.ROLE_MANAGER)
        );

        when(staffUserService.createUser(any(CreateStaffUserRequest.class)))
            .thenThrow(new UsernameAlreadyExistsException("admin"));

        mockMvc.perform(post("/api/identity/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
    }

    @Test
    void createUser_MissingFields_Returns400() throws Exception {
        CreateStaffUserRequest request = new CreateStaffUserRequest(
            "", "short", "", Set.of() // Invalid fields
        );

        mockMvc.perform(post("/api/identity/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void updateRoles_Success_Returns200() throws Exception {
        UpdateStaffUserRolesRequest request = new UpdateStaffUserRolesRequest(Set.of(Role.ROLE_MANAGER));
        StaffUserResponse response = new StaffUserResponse(
            2L, "jdoe", "John Doe", true, List.of("ROLE_MANAGER"), Instant.now(), Instant.now()
        );

        when(staffUserService.updateRoles(eq(2L), any(UpdateStaffUserRolesRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/identity/users/2/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0]").value("ROLE_MANAGER"));
    }

    @Test
    void updateRoles_NotFound_Returns404() throws Exception {
        UpdateStaffUserRolesRequest request = new UpdateStaffUserRolesRequest(Set.of(Role.ROLE_MANAGER));

        when(staffUserService.updateRoles(eq(99L), any(UpdateStaffUserRolesRequest.class)))
            .thenThrow(new StaffUserNotFoundException(99L));

        mockMvc.perform(put("/api/identity/users/99/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("STAFF_USER_NOT_FOUND"));
    }

    @Test
    void deactivateUser_Success_Returns204() throws Exception {
        doNothing().when(staffUserService).deactivateUser(eq(2L), isNull());

        mockMvc.perform(delete("/api/identity/users/2"))
            .andExpect(status().isNoContent());

        verify(staffUserService).deactivateUser(eq(2L), isNull());
    }

    @Test
    void deactivateUser_NotFound_Returns404() throws Exception {
        doThrow(new StaffUserNotFoundException(99L)).when(staffUserService).deactivateUser(eq(99L), isNull());

        mockMvc.perform(delete("/api/identity/users/99"))
            .andExpect(status().isNotFound());
    }

    // 422 CANNOT_DEACTIVATE_SELF is deferred to Phase 3.2 since principal is null
}
