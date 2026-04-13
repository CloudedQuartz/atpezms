package com.atpezms.atpezms.park.entity;

import com.atpezms.atpezms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Park Zone reference data.
 *
 * This is owned by the Park bounded context, but is seeded in Phase 1 so Ticketing
 * can reference zones immediately (Phase 1 seeds minimal Park reference data).
 */
@Entity
@Table(name = "zones")
public class Zone extends BaseEntity {
	@Column(name = "code", nullable = false, unique = true, length = 50)
	private String code;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	protected Zone() {
		// For JPA
	}

	public Zone(String code, String name) {
		this.code = code;
		this.name = name;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}
}
