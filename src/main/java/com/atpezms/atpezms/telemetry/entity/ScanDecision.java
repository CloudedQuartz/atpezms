package com.atpezms.atpezms.telemetry.entity;

/**
 * The outcome of a wristband scan.
 *
 * ALLOWED -- The scan was accepted; the visitor was granted access
 * DENIED  -- The scan was rejected; the visitor was denied access
 */
public enum ScanDecision {
	ALLOWED,
	DENIED
}
