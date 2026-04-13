package com.atpezms.atpezms.ticketing.entity;

/**
 * Stable identifier for a pass category (FR-VT1).
 *
 * Stored as a string in the database (see V001 CHECK constraint on pass_types.code).
 */
public enum PassTypeCode {
	SINGLE_DAY,
	MULTI_DAY,
	RIDE_SPECIFIC,
	FAMILY,
	FAST_TRACK
}
