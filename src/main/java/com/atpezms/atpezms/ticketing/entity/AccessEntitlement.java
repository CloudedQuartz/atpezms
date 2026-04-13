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

/**
 * Access grant attached to a {@link Ticket}.
 *
 * <p>This entity implements a flexible entitlement model. Instead of adding
 * fixed boolean columns to {@code tickets} (e.g. {@code hasFastTrack},
 * {@code hasRideXAccess}), each entitlement is stored as a separate row with
 * a type discriminator ({@link EntitlementType}). This makes the model
 * extensible without schema changes.
 *
 * <h2>Why zoneId/rideId are plain Long fields (not JPA relationships)</h2>
 * {@code zoneId} refers to Park context data and {@code rideId} refers to Rides
 * context data. DESIGN.md §6.2 forbids cross-context JPA relationships.
 * Therefore these are stored as scalar IDs, and validated via service calls.
 */
@Entity
@Table(name = "access_entitlements")
public class AccessEntitlement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "entitlement_type", nullable = false, length = 20)
    private EntitlementType entitlementType;

    @Column(name = "zone_id")
    private Long zoneId;

    @Column(name = "ride_id")
    private Long rideId;

    @Column(name = "priority_level")
    private Integer priorityLevel;

    protected AccessEntitlement() {
        // For JPA
    }

    public AccessEntitlement(
            Ticket ticket,
            EntitlementType entitlementType,
            Long zoneId,
            Long rideId,
            Integer priorityLevel) {
        if (ticket == null) {
            throw new IllegalArgumentException("ticket is required");
        }
        if (entitlementType == null) {
            throw new IllegalArgumentException("entitlementType is required");
        }

        this.ticket = ticket;
        this.entitlementType = entitlementType;
        this.zoneId = zoneId;
        this.rideId = rideId;
        this.priorityLevel = priorityLevel;

        validateTypeSpecificFieldsForArguments();
    }

    @PrePersist
    @PreUpdate
    private void validate() {
        if (ticket == null) {
            throw new IllegalStateException("AccessEntitlement.ticket is required");
        }
        if (entitlementType == null) {
            throw new IllegalStateException("AccessEntitlement.entitlementType is required");
        }
        validateTypeSpecificFieldsForState();
    }

    private void validateTypeSpecificFieldsForArguments() {
        switch (entitlementType) {
            case ZONE -> {
                if (zoneId == null || zoneId <= 0) {
                    throw new IllegalArgumentException("ZONE entitlement requires a positive zoneId");
                }
                if (rideId != null) {
                    throw new IllegalArgumentException("ZONE entitlement must not have rideId");
                }
                if (priorityLevel != null) {
                    throw new IllegalArgumentException("ZONE entitlement must not have priorityLevel");
                }
            }
            case RIDE -> {
                if (rideId == null || rideId <= 0) {
                    throw new IllegalArgumentException("RIDE entitlement requires a positive rideId");
                }
                if (zoneId != null) {
                    throw new IllegalArgumentException("RIDE entitlement must not have zoneId");
                }
                if (priorityLevel != null) {
                    throw new IllegalArgumentException("RIDE entitlement must not have priorityLevel");
                }
            }
            case QUEUE_PRIORITY -> {
                if (priorityLevel == null || priorityLevel <= 0) {
                    throw new IllegalArgumentException(
                            "QUEUE_PRIORITY entitlement requires a positive priorityLevel");
                }
                if (zoneId != null) {
                    throw new IllegalArgumentException("QUEUE_PRIORITY entitlement must not have zoneId");
                }
                if (rideId != null) {
                    throw new IllegalArgumentException("QUEUE_PRIORITY entitlement must not have rideId");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported entitlement type: " + entitlementType);
        }
    }

    private void validateTypeSpecificFieldsForState() {
        switch (entitlementType) {
            case ZONE -> {
                if (zoneId == null || zoneId <= 0) {
                    throw new IllegalStateException("ZONE entitlement requires a positive zoneId");
                }
                if (rideId != null) {
                    throw new IllegalStateException("ZONE entitlement must not have rideId");
                }
                if (priorityLevel != null) {
                    throw new IllegalStateException("ZONE entitlement must not have priorityLevel");
                }
            }
            case RIDE -> {
                if (rideId == null || rideId <= 0) {
                    throw new IllegalStateException("RIDE entitlement requires a positive rideId");
                }
                if (zoneId != null) {
                    throw new IllegalStateException("RIDE entitlement must not have zoneId");
                }
                if (priorityLevel != null) {
                    throw new IllegalStateException("RIDE entitlement must not have priorityLevel");
                }
            }
            case QUEUE_PRIORITY -> {
                if (priorityLevel == null || priorityLevel <= 0) {
                    throw new IllegalStateException(
                            "QUEUE_PRIORITY entitlement requires a positive priorityLevel");
                }
                if (zoneId != null) {
                    throw new IllegalStateException("QUEUE_PRIORITY entitlement must not have zoneId");
                }
                if (rideId != null) {
                    throw new IllegalStateException("QUEUE_PRIORITY entitlement must not have rideId");
                }
            }
            default -> throw new IllegalStateException("Unsupported entitlement type: " + entitlementType);
        }
    }

    public Ticket getTicket() {
        return ticket;
    }

    public EntitlementType getEntitlementType() {
        return entitlementType;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public Long getRideId() {
        return rideId;
    }

    public Integer getPriorityLevel() {
        return priorityLevel;
    }
}
