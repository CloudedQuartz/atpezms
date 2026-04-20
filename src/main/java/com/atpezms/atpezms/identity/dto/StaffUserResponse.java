package com.atpezms.atpezms.identity.dto;

import com.atpezms.atpezms.identity.entity.Role;
import com.atpezms.atpezms.identity.entity.StaffUser;

import java.time.Instant;
import java.util.List;

public record StaffUserResponse(
    Long id,
    String username,
    String fullName,
    boolean active,
    List<String> roles,
    Instant createdAt,
    Instant updatedAt
) {
    public static StaffUserResponse from(StaffUser user) {
        return new StaffUserResponse(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.isActive(),
            user.getRoles().stream().map(Role::name).sorted().toList(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
