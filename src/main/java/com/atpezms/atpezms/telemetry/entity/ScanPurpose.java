package com.atpezms.atpezms.telemetry.entity;

/**
 * The purpose/reason for a wristband scan.
 *
 * RIDE_ENTRY    -- Visitor scanned to enter a ride queue
 * RIDE_EXIT     -- Visitor scanned to exit a ride queue (left without boarding)
 * POS_SALE      -- Visitor scanned at a POS terminal for food/merchandise purchase
 * KIOSK_RESERVATION -- Visitor scanned at a kiosk to make an event reservation
 * GATE_ENTRY    -- Visitor scanned to enter the park (visit start)
 * GATE_EXIT     -- Visitor scanned to exit the park (visit end, checkout)
 */
public enum ScanPurpose {
	RIDE_ENTRY,
	RIDE_EXIT,
	POS_SALE,
	KIOSK_RESERVATION,
	GATE_ENTRY,
	GATE_EXIT
}
