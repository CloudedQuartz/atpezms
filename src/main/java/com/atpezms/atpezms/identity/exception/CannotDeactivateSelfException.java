package com.atpezms.atpezms.identity.exception;

import com.atpezms.atpezms.common.exception.BusinessRuleViolationException;

public class CannotDeactivateSelfException extends BusinessRuleViolationException {
    public CannotDeactivateSelfException() {
        super("CANNOT_DEACTIVATE_SELF", "An admin cannot deactivate their own account");
    }
}
