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
 * A physical RFID wristband.
 *
 * <p>The {@code rfid_tag} must be globally unique. The wristband is the sole
 * identifier for all visitor interactions (CO-1).
 */
@Entity
@Table(name = "wristbands")
public class Wristband extends BaseEntity {

    @Column(name = "rfid_tag", nullable = false, unique = true, length = 64)
    private String rfidTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private WristbandStatus status;

    protected Wristband() {
        // For JPA
    }

    /**
     * Creates a new wristband with the given RFID tag.
     * By default, a new wristband is {@code IN_STOCK}.
     *
     * @param rfidTag the unique RFID tag string
     */
    public Wristband(String rfidTag) {
        if (rfidTag == null || rfidTag.isBlank()) {
            throw new IllegalArgumentException("rfidTag is required");
        }
        this.rfidTag = rfidTag;
        this.status = WristbandStatus.IN_STOCK;
    }

    @PrePersist
    @PreUpdate
    private void validate() {
        if (rfidTag == null || rfidTag.isBlank()) {
            throw new IllegalStateException("rfidTag is required");
        }
        if (status == null) {
            throw new IllegalStateException("status is required");
        }
    }

    public String getRfidTag() {
        return rfidTag;
    }

    public WristbandStatus getStatus() {
        return status;
    }

    /**
     * Activates the wristband for a new visit.
     *
     * @throws IllegalStateException if the wristband is not IN_STOCK
     */
    public void activate() {
        if (this.status != WristbandStatus.IN_STOCK) {
            throw new IllegalStateException(
                    "Cannot activate a wristband that is " + this.status);
        }
        this.status = WristbandStatus.ACTIVE;
    }

    /**
     * Returns the wristband to stock (e.g. when a visit ends successfully).
     *
     * @throws IllegalStateException if the wristband is not ACTIVE
     */
    public void returnToStock() {
        if (this.status != WristbandStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot return a wristband to stock that is " + this.status);
        }
        this.status = WristbandStatus.IN_STOCK;
    }

    /**
     * Deactivates the wristband permanently (e.g. lost or stolen).
     */
    public void deactivate() {
        this.status = WristbandStatus.DEACTIVATED;
    }
}
