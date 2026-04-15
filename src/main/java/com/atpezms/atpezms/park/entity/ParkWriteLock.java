package com.atpezms.atpezms.park.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Single-row concurrency anchor for serialized park configuration writes.
 *
 * <h2>Why this exists</h2>
 * Two types of Park writes have invariants that require serialization:
 * <ol>
 *   <li>Activating a new {@link ParkConfiguration}: exactly one row must be
 *       {@code active = true} at all times. Without serialization, two
 *       concurrent requests could both deactivate the current config and
 *       both insert new active rows, producing two active configurations.</li>
 *   <li>Creating a new {@link SeasonalPeriod}: seasonal periods must not
 *       overlap. Without serialization, two concurrent creates could both pass
 *       the overlap check before either commits.</li>
 * </ol>
 *
 * <h2>How it works</h2>
 * Before any write to {@code park_configurations} or {@code seasonal_periods},
 * the service acquires a {@code PESSIMISTIC_WRITE} lock on this row via
 * {@link com.atpezms.atpezms.park.repository.ParkWriteLockRepository#acquireLock()}.
 * The database serializes all holders of that lock, so only one thread can
 * execute the critical section at a time.
 *
 * <h2>Why a dedicated table and not an existing row</h2>
 * {@code seasonal_periods} can be completely empty (all periods deleted).
 * A dedicated lock table always has the anchor row, so the locking strategy
 * works even when the guarded tables are empty.
 *
 * <h2>This entity is never written to by application code</h2>
 * The single row (id=1) is seeded by Flyway V006 and never modified.
 * It exists solely to be locked.
 *
 * <p>Note: this entity does NOT extend {@link com.atpezms.atpezms.common.entity.BaseEntity}
 * because BaseEntity requires both {@code created_at} and {@code updated_at}
 * columns, and the lock table only needs {@code id} and {@code created_at}.
 * Adding {@code updated_at} to a table that is never updated would be misleading.
 */
@Entity
@Table(name = "park_write_lock")
public class ParkWriteLock {

	@Id
	private int id;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ParkWriteLock() {
		// For JPA
	}

	public int getId() {
		return id;
	}
}
