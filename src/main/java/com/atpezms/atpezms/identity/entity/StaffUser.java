package com.atpezms.atpezms.identity.entity;

import com.atpezms.atpezms.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "staff_users")
public class StaffUser extends BaseEntity {

    @Column(name = "username", nullable = false, length = 100, updatable = false)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "staff_user_roles",
        joinColumns = @JoinColumn(name = "staff_user_id")
    )
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    protected StaffUser() {
        // JPA requires no-arg constructor
    }

    public StaffUser(String username, String passwordHash, String fullName, Set<Role> roles) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username must not be blank");
        if (passwordHash == null || passwordHash.isBlank()) throw new IllegalArgumentException("Password hash must not be blank");
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("Full name must not be blank");
        if (roles == null || roles.isEmpty()) throw new IllegalArgumentException("Roles must not be empty");

        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.roles = new HashSet<>(roles);
    }

    public void updateRoles(Set<Role> newRoles) {
        if (newRoles == null || newRoles.isEmpty()) {
            throw new IllegalArgumentException("Roles must not be empty");
        }
        this.roles = new HashSet<>(newRoles);
    }

    public void deactivate() {
        this.active = false;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isActive() {
        return active;
    }

    public Set<Role> getRoles() {
        return new HashSet<>(roles);
    }
}
