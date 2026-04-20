package com.atpezms.atpezms.identity.exception;

import com.atpezms.atpezms.common.exception.ResourceNotFoundException;

public class StaffUserNotFoundException extends ResourceNotFoundException {
    public StaffUserNotFoundException(Long id) {
        super("STAFF_USER_NOT_FOUND", "Staff user not found with ID: " + id);
    }
}
