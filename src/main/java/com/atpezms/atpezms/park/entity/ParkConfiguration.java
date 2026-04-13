package com.atpezms.atpezms.park.entity;

import com.atpezms.atpezms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Park-wide operational configuration.
 *
 * Phase 1 uses this as reference data (seeded by Flyway) so Ticketing can read
 * the active configuration when initializing {@code park_day_capacity} rows for
 * a date (FR-VT3). Capacity enforcement itself is implemented via guarded atomic
 * updates on {@code park_day_capacity} (see Phase 1 design).
 */
@Entity
@Table(name = "park_configurations")
public class ParkConfiguration extends BaseEntity {
	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "max_daily_capacity", nullable = false)
	private int maxDailyCapacity;

	protected ParkConfiguration() {
		// For JPA
	}

	public ParkConfiguration(boolean active, int maxDailyCapacity) {
		if (maxDailyCapacity <= 0) {
			throw new IllegalArgumentException("maxDailyCapacity must be > 0");
		}
		this.active = active;
		this.maxDailyCapacity = maxDailyCapacity;
	}

	public boolean isActive() {
		return active;
	}

	public int getMaxDailyCapacity() {
		return maxDailyCapacity;
	}
}
