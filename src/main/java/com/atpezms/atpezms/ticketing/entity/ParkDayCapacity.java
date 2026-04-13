package com.atpezms.atpezms.ticketing.entity;

import com.atpezms.atpezms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Per-day capacity counter used for concurrency-safe ticket issuance.
 *
 * <p>Why this table exists:
 * Counting rows in {@code visits} or {@code tickets} at issuance time with
 * {@code SELECT COUNT(*)} is race-prone under concurrency:
 *
 * <ul>
 *   <li>Counter A reads count=4999 (max=5000)</li>
 *   <li>Counter B reads count=4999 (max=5000)</li>
 *   <li>Both decide "one slot left" and issue tickets</li>
 *   <li>Final count becomes 5001 (over capacity)</li>
 * </ul>
 *
 * <p>Instead, issuance uses an atomic guarded update on one row:
 *
 * <pre>
 * UPDATE park_day_capacity
 * SET issued_count = issued_count + 1
 * WHERE visit_date = ? AND issued_count < max_capacity
 * </pre>
 *
 * If the update affects 1 row, reservation succeeded.
 * If it affects 0 rows, the day is sold out (or the date row does not exist).
 *
 * <p>This makes the database the concurrency control point and prevents
 * overbooking under concurrent requests.
 */
@Entity
@Table(name = "park_day_capacity")
public class ParkDayCapacity extends BaseEntity {

    @Column(name = "visit_date", nullable = false, unique = true)
    private LocalDate visitDate;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    protected ParkDayCapacity() {
        // For JPA
    }

    /**
     * Creates a day-capacity row.
     *
     * @param visitDate date this row tracks
     * @param maxCapacity snapshot of active park max capacity at row creation
     */
    public ParkDayCapacity(LocalDate visitDate, int maxCapacity) {
        if (visitDate == null) {
            throw new IllegalArgumentException("visitDate is required");
        }
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("maxCapacity must be > 0");
        }
        this.visitDate = visitDate;
        this.maxCapacity = maxCapacity;
        this.issuedCount = 0;
    }

    @PrePersist
    @PreUpdate
    private void validate() {
        if (visitDate == null) {
            throw new IllegalStateException("ParkDayCapacity.visitDate is required");
        }
        if (maxCapacity <= 0) {
            throw new IllegalStateException("ParkDayCapacity.maxCapacity must be > 0");
        }
        if (issuedCount < 0) {
            throw new IllegalStateException("ParkDayCapacity.issuedCount must be >= 0");
        }
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public int getIssuedCount() {
        return issuedCount;
    }
}
