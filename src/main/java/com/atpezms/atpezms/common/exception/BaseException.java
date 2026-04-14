package com.atpezms.atpezms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all business/domain exceptions.
 *
 * We do not use 401/403 for business rules. Those are reserved strictly for authn/authz
 * and will be introduced in the Identity/Security slice.
 */
public abstract class BaseException extends RuntimeException {
	private final String errorCode;

	protected BaseException(String errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public abstract HttpStatus getHttpStatus();
}
