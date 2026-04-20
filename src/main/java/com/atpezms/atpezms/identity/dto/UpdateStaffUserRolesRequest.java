package com.atpezms.atpezms.identity.dto;

import com.atpezms.atpezms.identity.entity.Role;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record UpdateStaffUserRolesRequest(
    @NotEmpty 
    Set<Role> roles
) {}
