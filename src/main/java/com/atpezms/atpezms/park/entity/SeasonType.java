package com.atpezms.atpezms.park.entity;

/**
 * Season classification for pricing.
 *
 * Stored as a string in the database (see V001 CHECK constraint on seasonal_periods.season_type).
 */
public enum SeasonType {
	PEAK,
	OFF_PEAK
}
