package com.atpezms.atpezms.park.exception;

import com.atpezms.atpezms.common.exception.DuplicateResourceException;

/** Thrown when a zone creation request uses a code that already exists. */
public class ZoneCodeAlreadyExistsException extends DuplicateResourceException {
	public ZoneCodeAlreadyExistsException(String code) {
		super("ZONE_CODE_ALREADY_EXISTS", "A zone with code '" + code + "' already exists");
	}
}
