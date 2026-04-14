package com.atpezms.atpezms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 422 category: request is well-formed but violates a domain rule.
 */
public class BusinessRuleViolationException extends BaseException {
	public BusinessRuleViolationException(String errorCode, String message) {
		super(errorCode, message);
	}

	@Override
	public HttpStatus getHttpStatus() {
		return HttpStatus.UNPROCESSABLE_ENTITY;
	}
}
