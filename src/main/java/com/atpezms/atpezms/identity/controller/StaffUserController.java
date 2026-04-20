package com.atpezms.atpezms.identity.controller;

import com.atpezms.atpezms.identity.dto.CreateStaffUserRequest;
import com.atpezms.atpezms.identity.dto.StaffUserResponse;
import com.atpezms.atpezms.identity.dto.UpdateStaffUserRolesRequest;
import com.atpezms.atpezms.identity.service.StaffUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// Authentication import replaced with Principal until Phase 3.2
import java.security.Principal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/identity/users")
@Validated
public class StaffUserController {

    private final StaffUserService staffUserService;

    public StaffUserController(StaffUserService staffUserService) {
        this.staffUserService = staffUserService;
    }

    // Requires: ROLE_ADMIN
    @GetMapping
    public List<StaffUserResponse> listUsers() {
        return staffUserService.listUsers();
    }

    // Requires: ROLE_ADMIN
    @GetMapping("/{id}")
    public StaffUserResponse getUser(@PathVariable Long id) {
        return staffUserService.getUser(id);
    }

    // Requires: ROLE_ADMIN
    @PostMapping
    public ResponseEntity<StaffUserResponse> createUser(@Valid @RequestBody CreateStaffUserRequest request) {
        StaffUserResponse response = staffUserService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Requires: ROLE_ADMIN
    @PutMapping("/{id}/roles")
    public StaffUserResponse updateRoles(
            @PathVariable Long id, 
            @Valid @RequestBody UpdateStaffUserRolesRequest request) {
        return staffUserService.updateRoles(id, request);
    }

    // Requires: ROLE_ADMIN
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateUser(@PathVariable Long id, Principal principal) {
        String requestingUsername = principal == null ? null : principal.getName();
        staffUserService.deactivateUser(id, requestingUsername);
    }
}
