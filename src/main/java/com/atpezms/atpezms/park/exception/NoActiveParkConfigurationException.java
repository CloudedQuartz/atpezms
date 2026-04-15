package com.atpezms.atpezms.park.exception;

import com.atpezms.atpezms.common.exception.ResourceNotFoundException;

/**
 * Thrown when the system has no active park configuration.
 *
 * <p>This should not happen in a correctly seeded system (V001 seeds one active
 * row), but the endpoint is defensive so callers get a clear 404 rather than
 * an uncaught IllegalStateException.
 */
public class NoActiveParkConfigurationException extends ResourceNotFoundException {
	public NoActiveParkConfigurationException() {
		super("NO_ACTIVE_PARK_CONFIGURATION",
				"No active park configuration found; the system must have exactly one active configuration");
	}
}
