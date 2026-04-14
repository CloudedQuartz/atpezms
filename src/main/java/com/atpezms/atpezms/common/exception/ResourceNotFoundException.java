package com.atpezms.atpezms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 404 category: the requested entity does not exist.
 */
public class ResourceNotFoundException extends BaseException {
	public ResourceNotFoundException(String errorCode, String message) {
		super(errorCode, message);
	}

	@Override
	public HttpStatus getHttpStatus() {
		return HttpStatus.NOT_FOUND;
	}
}
