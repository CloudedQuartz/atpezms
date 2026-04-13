package com.atpezms.atpezms.ticketing.entity;

/**
 * Lifecycle status of an RFID wristband.
 */
public enum WristbandStatus {
    /** Known to the system, but not currently assigned to any visit. */
    IN_STOCK,
    
    /** Currently assigned to an active visit. */
    ACTIVE,
    
    /** Retired (lost, stolen, end-of-life). Must never be reused. */
    DEACTIVATED
}
