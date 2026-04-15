package com.atpezms.atpezms.park.exception;

import com.atpezms.atpezms.common.exception.ResourceNotFoundException;

/** Thrown when a zone lookup by ID fails (zone does not exist). */
public class ZoneNotFoundException extends ResourceNotFoundException {
	public ZoneNotFoundException(Long id) {
		super("ZONE_NOT_FOUND", "Zone not found: id=" + id);
	}
}
