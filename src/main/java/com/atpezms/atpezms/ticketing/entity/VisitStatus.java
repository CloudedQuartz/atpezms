package com.atpezms.atpezms.ticketing.entity;

/**
 * Lifecycle status of a park visit.
 */
public enum VisitStatus {
    /** The visitor is currently in the park. */
    ACTIVE,
    
    /** The visitor has checked out and left the park. */
    ENDED
}
