package com.atpezms.atpezms.identity.service;

import com.atpezms.atpezms.identity.dto.CreateStaffUserRequest;
import com.atpezms.atpezms.identity.dto.StaffUserResponse;
import com.atpezms.atpezms.identity.dto.UpdateStaffUserRolesRequest;
import com.atpezms.atpezms.identity.entity.StaffUser;
import com.atpezms.atpezms.identity.exception.CannotDeactivateSelfException;
import com.atpezms.atpezms.identity.exception.StaffUserNotFoundException;
import com.atpezms.atpezms.identity.exception.UsernameAlreadyExistsException;
import com.atpezms.atpezms.identity.repository.StaffUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class StaffUserService {

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;

    public StaffUserService(StaffUserRepository staffUserRepository, PasswordEncoder passwordEncoder) {
        this.staffUserRepository = staffUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<StaffUserResponse> listUsers() {
        return staffUserRepository.findAllByOrderByUsernameAsc()
            .stream()
            .map(StaffUserResponse::from)
            .toList();
    }

    public StaffUserResponse getUser(Long id) {
        return staffUserRepository.findById(id)
            .map(StaffUserResponse::from)
            .orElseThrow(() -> new StaffUserNotFoundException(id));
    }

    @Transactional
    public StaffUserResponse createUser(CreateStaffUserRequest request) {
        if (staffUserRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException(request.username());
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        
        StaffUser user = new StaffUser(
            request.username(),
            encodedPassword,
            request.fullName(),
            request.roles()
        );

        try {
            StaffUser savedUser = staffUserRepository.saveAndFlush(user);
            return StaffUserResponse.from(savedUser);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent request won the race on the unique constraint.
            throw new UsernameAlreadyExistsException(request.username());
        }
    }

    @Transactional
    public StaffUserResponse updateRoles(Long id, UpdateStaffUserRolesRequest request) {
        StaffUser user = staffUserRepository.findById(id)
            .orElseThrow(() -> new StaffUserNotFoundException(id));
            
        user.updateRoles(request.roles());
        // Flush to trigger @PreUpdate (auditing timestamps) so the response
        // reflects the current updatedAt, not the stale pre-update value.
        staffUserRepository.saveAndFlush(user);
        return StaffUserResponse.from(user);
    }

    @Transactional
    public void deactivateUser(Long id, String requestingUsername) {
        StaffUser user = staffUserRepository.findById(id)
            .orElseThrow(() -> new StaffUserNotFoundException(id));
            
        if (user.getUsername().equals(requestingUsername)) {
            throw new CannotDeactivateSelfException();
        }
        
        user.deactivate();
        staffUserRepository.saveAndFlush(user);
    }
}
