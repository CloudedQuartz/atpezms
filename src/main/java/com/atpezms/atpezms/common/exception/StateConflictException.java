package com.atpezms.atpezms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 409 category: operation cannot proceed given current resource state.
 */
public class StateConflictException extends BaseException {
	public StateConflictException(String errorCode, String message) {
		super(errorCode, message);
	}

	@Override
	public HttpStatus getHttpStatus() {
		return HttpStatus.CONFLICT;
	}
}
