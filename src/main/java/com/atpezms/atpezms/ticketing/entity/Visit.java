package com.atpezms.atpezms.ticketing.entity;

import com.atpezms.atpezms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * An active session in the park.
 *
 * <p>Created when a visitor registers and enters the park (a wristband is
 * activated). Used by the PR-1 hot path to resolve an RFID tag back to
 * a visitor and their ticket entitlements.
 *
 * <h2>Intra-context associations</h2>
 * Like {@link Ticket}, {@code Visit} uses {@code @ManyToOne} to link to
 * {@link Visitor}, {@link Wristband}, and {@link Ticket} because all
 * reside inside the Ticketing context.
 */
@Entity
@Table(name = "visits")
public class Visit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visitor_id", nullable = false)
    private Visitor visitor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wristband_id", nullable = false)
    private Wristband wristband;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private VisitStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected Visit() {
        // For JPA
    }

    /**
     * Starts a new visit.
     */
    public Visit(Visitor visitor, Wristband wristband, Ticket ticket, Instant startedAt) {
        if (visitor == null) {
            throw new IllegalArgumentException("visitor is required");
        }
        if (wristband == null) {
            throw new IllegalArgumentException("wristband is required");
        }
        if (ticket == null) {
            throw new IllegalArgumentException("ticket is required");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt is required");
        }

        this.visitor = visitor;
        this.wristband = wristband;
        this.ticket = ticket;
        this.startedAt = startedAt;
        this.status = VisitStatus.ACTIVE;
    }

    @PrePersist
    @PreUpdate
    private void validate() {
        if (visitor == null) {
            throw new IllegalStateException("Visit.visitor is required");
        }
        if (wristband == null) {
            throw new IllegalStateException("Visit.wristband is required");
        }
        if (ticket == null) {
            throw new IllegalStateException("Visit.ticket is required");
        }
        if (startedAt == null) {
            throw new IllegalStateException("Visit.startedAt is required");
        }
        if (status == null) {
            throw new IllegalStateException("Visit.status is required");
        }
    }

    public Visitor getVisitor() {
        return visitor;
    }

    public Wristband getWristband() {
        return wristband;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public VisitStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    /**
     * Ends the visit.
     *
     * @param endedAt the time the visitor checked out
     * @throws IllegalStateException if the visit is already ended
     */
    public void endVisit(Instant endedAt) {
        if (this.status != VisitStatus.ACTIVE) {
            throw new IllegalStateException("Cannot end a visit that is " + this.status);
        }
        if (endedAt == null) {
            throw new IllegalArgumentException("endedAt is required");
        }
        if (endedAt.isBefore(this.startedAt)) {
            throw new IllegalArgumentException("endedAt cannot be before startedAt");
        }
        this.status = VisitStatus.ENDED;
        this.endedAt = endedAt;
    }
}
