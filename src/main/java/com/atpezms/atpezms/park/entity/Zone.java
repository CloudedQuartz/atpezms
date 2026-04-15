package com.atpezms.atpezms.park.entity;

import com.atpezms.atpezms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A logical area of the park (Adventure Zone, Water Zone, etc.).
 *
 * <p>Owned by the Park bounded context. Zones are referenced by Ticketing
 * (for entitlement issuance), Rides, Food, Merchandise, and Events -- always by
 * their ID, never via a cross-context JPA relationship (DESIGN.md §6.2).
 *
 * <h2>Phase 1 vs Phase 2</h2>
 * Phase 1 seeded zones with only {@code code} and {@code name} as minimal
 * reference data to unblock Ticketing. Phase 2 adds:
 * <ul>
 *   <li>{@code description} -- human-readable zone description for staff UIs.</li>
 *   <li>{@code active} -- operational open/closed flag.</li>
 * </ul>
 *
 * <h2>What {@code active} means</h2>
 * {@code active = false} means the zone is <em>physically closed</em> right now
 * (renovation, safety incident, etc.). It does NOT stop entitlement issuance --
 * a visitor who buys a full-park ticket still receives a ZONE entitlement for
 * every zone, because they paid for it. Phase 6 (Rides) enforces the operational
 * flag at scan time: entry is denied even if the entitlement exists.
 *
 * <h2>Why {@code code} is immutable</h2>
 * The code is the stable human identifier for a zone across logs, documentation,
 * and external system configurations. Allowing a code change after zones are
 * referenced by other contexts' data would silently corrupt those references.
 */
@Entity
@Table(name = "zones")
public class Zone extends BaseEntity {

	// updatable=false enforces immutability at the JPA layer: Hibernate will never
	// include this column in an UPDATE statement, even if someone accidentally
	// calls a setter. The business rule (codes don't change) is now enforceable
	// at the framework level, not just by convention.
	@Column(name = "code", nullable = false, unique = true, length = 50, updatable = false)
	private String code;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "description", length = 500)
	private String description;

	/**
	 * Operational open/closed flag. See class Javadoc for semantics.
	 * Defaults to {@code true} (open) to match the DB column default.
	 */
	@Column(name = "active", nullable = false)
	private boolean active = true;

	protected Zone() {
		// For JPA
	}

	/**
	 * Creates a new zone with the given code and name, active by default.
	 *
	 * @param code        stable uppercase identifier, e.g. {@code ADVENTURE}
	 * @param name        human-readable display name
	 * @param description optional longer description (may be null)
	 * @param active      initial active status
	 */
	public Zone(String code, String name, String description, boolean active) {
		this.code = code;
		this.name = name;
		this.description = description;
		this.active = active;
	}

	/**
	 * Updates the mutable fields of this zone.
	 *
	 * <p>{@code code} is intentionally not updatable; see class Javadoc.
	 *
	 * @param name        new display name (required)
	 * @param description new description (null clears it)
	 * @param active      new operational status
	 */
	public void update(String name, String description, boolean active) {
		this.name = name;
		this.description = description;
		this.active = active;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public boolean isActive() {
		return active;
	}
}
