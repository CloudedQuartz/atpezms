package com.atpezms.atpezms.identity.exception;

import com.atpezms.atpezms.common.exception.DuplicateResourceException;

public class UsernameAlreadyExistsException extends DuplicateResourceException {
    public UsernameAlreadyExistsException(String username) {
        super("USERNAME_ALREADY_EXISTS", "Username already exists: " + username);
    }
}
