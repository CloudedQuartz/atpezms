package com.atpezms.atpezms.ticketing.entity;

/**
 * Type of access entitlement granted by a ticket.
 *
 * <p>Each {@link AccessEntitlement} row stores exactly one type. The meaning
 * of nullable columns in that row depends on this enum value:
 *
 * <ul>
 *   <li>{@link #ZONE}: {@code zoneId} must be set; {@code rideId} and
 *       {@code priorityLevel} must be null.</li>
 *   <li>{@link #RIDE}: {@code rideId} must be set; {@code zoneId} and
 *       {@code priorityLevel} must be null.</li>
 *   <li>{@link #QUEUE_PRIORITY}: {@code priorityLevel} must be set;
 *       {@code zoneId} and {@code rideId} must be null.</li>
 * </ul>
 */
public enum EntitlementType {
    ZONE,
    RIDE,
    QUEUE_PRIORITY
}
