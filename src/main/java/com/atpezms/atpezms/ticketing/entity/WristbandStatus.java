package com.atpezms.atpezms.ticketing.entity;

/**
 * Lifecycle status of an RFID wristband.
 *
 * <p>The four states form a directed lifecycle. Not every transition is valid;
 * see {@link Wristband} for the guarded transition methods.
 *
 * <pre>
 *  IN_STOCK ──(issuance)──► ACTIVE ──(non-final-day checkout)──► INACTIVE
 *                              │                                      │
 *                              │◄────────(day-N re-entry)─────────────┘
 *                              │
 *                              └──(final-day checkout / deactivation)──► DEACTIVATED
 *  (any state) ──(staff deactivation: lost/stolen/damaged)──► DEACTIVATED
 * </pre>
 */
public enum WristbandStatus {

    /**
     * In the physical stockroom. Never issued to any visitor.
     * Can be activated for any new issuance.
     */
    IN_STOCK,

    /**
     * Visitor is <em>currently inside the park</em> in an active Visit session.
     *
     * <p>This is the signal every scan-processing context (Rides, Food, Merchandise)
     * uses to decide whether to allow an interaction. An ACTIVE wristband must
     * always have exactly one ACTIVE Visit behind it.
     */
    ACTIVE,

    /**
     * Issued to a specific visitor; physically on their wrist between visit sessions.
     *
     * <p>Used for multi-day passes: after the visitor exits at the end of a day
     * (checkout settles the bill and ends the Visit), their wristband transitions
     * to INACTIVE rather than DEACTIVATED, because they will return for the next
     * day of their pass. The wristband stays on their wrist overnight.
     *
     * <p>An INACTIVE wristband can only be re-activated for the visitor's
     * existing valid ticket (day-N entry). It cannot be issued to a stranger.
     *
     * <p>Transition into this state is implemented in Phase 4 (checkout /
     * Visit-end orchestration).
     */
    INACTIVE,

    /**
     * Permanently retired. Never reused under any circumstances.
     *
     * <p>Applies when:
     * <ul>
     *   <li>A single-day pass visitor completes checkout (visit fully over).</li>
     *   <li>A multi-day pass visitor completes their final day's checkout.</li>
     *   <li>Staff deactivate the wristband (lost, stolen, or damaged).</li>
     * </ul>
     */
    DEACTIVATED
}
