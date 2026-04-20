package com.atpezms.atpezms.identity.dto;

import com.atpezms.atpezms.identity.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateStaffUserRequest(
    @NotBlank 
    @Size(min = 3, max = 100) 
    @Pattern(regexp = "[a-z][a-z0-9._-]*") 
    String username,
    
    @NotBlank 
    @Size(min = 8) 
    String password,
    
    @NotBlank 
    @Size(max = 200) 
    String fullName,
    
    @NotEmpty 
    Set<Role> roles
) {}
