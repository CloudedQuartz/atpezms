package com.atpezms.atpezms.ticketing.entity;

/**
 * Age bands for pricing calculations.
 *
 * <p>Rules (from PHASE_01_TICKETING_DESIGN.md):
 * <ul>
 *   <li>CHILD: &lt; 12</li>
 *   <li>ADULT: 12 to 59</li>
 *   <li>SENIOR: &ge; 60</li>
 * </ul>
 */
public enum AgeGroup {
    CHILD,
    ADULT,
    SENIOR
}
