package com.atpezms.atpezms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 409 category: creation/update would violate a uniqueness constraint.
 */
public class DuplicateResourceException extends BaseException {
	public DuplicateResourceException(String errorCode, String message) {
		super(errorCode, message);
	}

	@Override
	public HttpStatus getHttpStatus() {
		return HttpStatus.CONFLICT;
	}
}
