package com.atpezms.atpezms.identity;

import com.atpezms.atpezms.identity.dto.CreateStaffUserRequest;
import com.atpezms.atpezms.identity.dto.UpdateStaffUserRolesRequest;
import com.atpezms.atpezms.identity.entity.Role;
import com.atpezms.atpezms.identity.entity.StaffUser;
import com.atpezms.atpezms.identity.repository.StaffUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StaffUserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Test
    void shouldCreateRetrieveAndDeactivateStaffUser() throws Exception {
        // 1. Create a user
        CreateStaffUserRequest createRequest = new CreateStaffUserRequest(
            "testuser", "securepass", "Test User", Set.of(Role.ROLE_TICKET_STAFF)
        );

        String createResponseJson = mockMvc.perform(post("/api/identity/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("testuser"))
            .andExpect(jsonPath("$.fullName").value("Test User"))
            .andExpect(jsonPath("$.roles[0]").value("ROLE_TICKET_STAFF"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        Long newUserId = objectMapper.readTree(createResponseJson).get("id").asLong();

        // Verify hash stored, not plaintext
        StaffUser savedUser = staffUserRepository.findById(newUserId).orElseThrow();
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("securepass");
        assertThat(savedUser.getPasswordHash()).startsWith("$2a$12$"); // BCrypt

        // 2. Retrieve user
        mockMvc.perform(get("/api/identity/users/" + newUserId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("testuser"));

        // 3. Update roles
        UpdateStaffUserRolesRequest updateRequest = new UpdateStaffUserRolesRequest(
            Set.of(Role.ROLE_MANAGER, Role.ROLE_ADMIN)
        );

        mockMvc.perform(put("/api/identity/users/" + newUserId + "/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"))
            .andExpect(jsonPath("$.roles[1]").value("ROLE_MANAGER"));

        // Verify persistence of roles
        StaffUser updatedUser = staffUserRepository.findById(newUserId).orElseThrow();
        assertThat(updatedUser.getRoles()).containsExactlyInAnyOrder(Role.ROLE_MANAGER, Role.ROLE_ADMIN);

        // 4. Deactivate user
        mockMvc.perform(delete("/api/identity/users/" + newUserId))
            .andExpect(status().isNoContent());

        // Verify deactivation
        StaffUser deactivatedUser = staffUserRepository.findById(newUserId).orElseThrow();
        assertThat(deactivatedUser.isActive()).isFalse();
    }
    
    @Test
    void shouldReturnSeedAdmin() throws Exception {
        // V007 seeds admin; use dynamic ID lookup since auto-increment assigns it
        StaffUser admin = staffUserRepository.findByUsername("admin").orElseThrow();

        mockMvc.perform(get("/api/identity/users/" + admin.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"));
    }

    @Test
    void shouldReturn409WhenCreatingDuplicateUsername() throws Exception {
        CreateStaffUserRequest request = new CreateStaffUserRequest(
            "duplicate", "password123", "First User", Set.of(Role.ROLE_TICKET_STAFF)
        );

        // First creation succeeds
        mockMvc.perform(post("/api/identity/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        // Second creation with same username fails with 409
        CreateStaffUserRequest request2 = new CreateStaffUserRequest(
            "duplicate", "differentPwd", "Second User", Set.of(Role.ROLE_MANAGER)
        );
        mockMvc.perform(post("/api/identity/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
    }

    @Test
    void shouldReturn404WhenGettingNonExistentUser() throws Exception {
        mockMvc.perform(get("/api/identity/users/99999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("STAFF_USER_NOT_FOUND"));
    }

    @Test
    void shouldReturn400WhenCreatingUserWithInvalidUsername() throws Exception {
        // Username must start with a lowercase letter
        CreateStaffUserRequest request = new CreateStaffUserRequest(
            "123bad", "password123", "Bad Username", Set.of(Role.ROLE_TICKET_STAFF)
        );

        mockMvc.perform(post("/api/identity/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldReturn400WhenCreatingUserWithShortPassword() throws Exception {
        CreateStaffUserRequest request = new CreateStaffUserRequest(
            "gooduser", "short", "Short Password", Set.of(Role.ROLE_TICKET_STAFF)
        );

        mockMvc.perform(post("/api/identity/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
