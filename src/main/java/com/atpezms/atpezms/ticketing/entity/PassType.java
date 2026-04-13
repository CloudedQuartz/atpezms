package com.atpezms.atpezms.ticketing.entity;

import com.atpezms.atpezms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Pass type configuration (what kinds of tickets can be sold).
 *
 * These are seeded via Flyway in Phase 1 so the system has a baseline set of
 * pass categories and Ticketing can list them once the listing endpoint is implemented.
 */
@Entity
@Table(name = "pass_types")
public class PassType extends BaseEntity {
	@Enumerated(EnumType.STRING)
	@Column(name = "code", nullable = false, unique = true, length = 20)
	private PassTypeCode code;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "multi_day_count")
	private Integer multiDayCount;

	@Column(name = "active", nullable = false)
	private boolean active;

	protected PassType() {
		// For JPA
	}

	@PrePersist
	@PreUpdate
	private void validate() {
		// Constructors are not executed when JPA hydrates entities from the DB.
		// We validate here so invalid state fails fast at persistence boundaries.
		if (code == null) {
			throw new IllegalStateException("code is required");
		}
		if (name == null || name.isBlank()) {
			throw new IllegalStateException("name is required");
		}
		if (code == PassTypeCode.MULTI_DAY) {
			if (multiDayCount == null || multiDayCount <= 0) {
				throw new IllegalStateException("multiDayCount must be > 0 for MULTI_DAY");
			}
		} else {
			if (multiDayCount != null) {
				throw new IllegalStateException("multiDayCount must be null unless code is MULTI_DAY");
			}
		}
	}

	public PassType(PassTypeCode code, String name, String description, Integer multiDayCount, boolean active) {
		if (code == null) {
			throw new IllegalArgumentException("code is required");
		}
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name is required");
		}
		if (code == PassTypeCode.MULTI_DAY) {
			if (multiDayCount == null || multiDayCount <= 0) {
				throw new IllegalArgumentException("multiDayCount must be > 0 for MULTI_DAY");
			}
		} else {
			if (multiDayCount != null) {
				throw new IllegalArgumentException("multiDayCount must be null unless code is MULTI_DAY");
			}
		}

		this.code = code;
		this.name = name;
		this.description = description;
		this.multiDayCount = multiDayCount;
		this.active = active;
	}

	public PassTypeCode getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public Integer getMultiDayCount() {
		return multiDayCount;
	}

	public boolean isActive() {
		return active;
	}
}
