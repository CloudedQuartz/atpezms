package com.atpezms.atpezms.common.entity;

/**
 * Season classification for pricing.
 *
 * This was moved to the common package because it is a shared value type used
 * across bounded contexts (by Park in seasonal_periods and by Ticketing in
 * pass_type_prices).
 *
 * Stored as a string in the database (see V001 CHECK constraint on
 * seasonal_periods.season_type and pass_type_prices.season_type).
 */
public enum SeasonType {
	PEAK,
	OFF_PEAK
}
