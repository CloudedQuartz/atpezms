package com.atpezms.atpezms.park.exception;

import com.atpezms.atpezms.common.exception.BusinessRuleViolationException;
import java.time.LocalDate;

/**
 * Thrown when activating a new ParkConfiguration would reduce max capacity
 * below the already-issued count for one or more future dates.
 *
 * <p>Capacity can only be reduced if no date in [today, ∞) has already reserved
 * more slots than the new maximum. The earliest conflicting date is included in
 * the message so operators know what to address.
 */
public class CapacityReductionConflictException extends BusinessRuleViolationException {
	public CapacityReductionConflictException(LocalDate earliestConflict, int newMax, int conflictingCount) {
		super("CAPACITY_REDUCTION_CONFLICT",
				"Cannot reduce max daily capacity to " + newMax
						+ ": date " + earliestConflict
						+ " already has " + conflictingCount + " reservations (would exceed new limit)");
	}
}
