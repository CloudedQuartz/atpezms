package com.atpezms.atpezms.ticketing.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AccessEntitlementTest {

    private Ticket sampleTicket() {
        Visitor visitor = new Visitor("A", "B", null, null, LocalDate.of(1990, 1, 1), 170);
        PassType passType = new PassType(PassTypeCode.SINGLE_DAY, "Single", "desc", null, true);
        LocalDate day = LocalDate.of(2026, 4, 15);
        return new Ticket(visitor, passType, day, day, day, 150000, "LKR", Instant.now());
    }

    @Test
    void shouldRequireZoneIdForZoneEntitlement() {
        assertThatThrownBy(() -> new AccessEntitlement(
                sampleTicket(), EntitlementType.ZONE, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zoneId");
    }

    @Test
    void shouldRequireRideIdForRideEntitlement() {
        assertThatThrownBy(() -> new AccessEntitlement(
                sampleTicket(), EntitlementType.RIDE, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rideId");
    }

    @Test
    void shouldRequirePriorityLevelForQueuePriorityEntitlement() {
        assertThatThrownBy(() -> new AccessEntitlement(
                sampleTicket(), EntitlementType.QUEUE_PRIORITY, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priorityLevel");
    }

    @Test
    void shouldRejectZoneEntitlementWithRideId() {
        assertThatThrownBy(() -> new AccessEntitlement(
                sampleTicket(), EntitlementType.ZONE, 1L, 2L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not have rideId");
    }

    @Test
    void shouldRejectQueuePriorityWithZoneId() {
        assertThatThrownBy(() -> new AccessEntitlement(
                sampleTicket(), EntitlementType.QUEUE_PRIORITY, 1L, null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not have zoneId");
    }

    @Test
    void shouldRejectZoneEntitlementWithPriorityLevel() {
        assertThatThrownBy(() -> new AccessEntitlement(
                sampleTicket(), EntitlementType.ZONE, 1L, null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not have priorityLevel");
    }

    @Test
    void shouldRejectZoneEntitlementWithNonPositiveZoneId() {
        assertThatThrownBy(() -> new AccessEntitlement(
                sampleTicket(), EntitlementType.ZONE, 0L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive zoneId");
    }

    @Test
    void shouldRejectRideEntitlementWithZoneId() {
        assertThatThrownBy(() -> new AccessEntitlement(
                sampleTicket(), EntitlementType.RIDE, 1L, 2L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not have zoneId");
    }

    @Test
    void shouldRejectRideEntitlementWithPriorityLevel() {
        assertThatThrownBy(() -> new AccessEntitlement(
                sampleTicket(), EntitlementType.RIDE, null, 2L, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not have priorityLevel");
    }

    @Test
    void shouldRejectRideEntitlementWithNonPositiveRideId() {
        assertThatThrownBy(() -> new AccessEntitlement(
                sampleTicket(), EntitlementType.RIDE, null, 0L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive rideId");
    }

    @Test
    void shouldRejectQueuePriorityWithRideId() {
        assertThatThrownBy(() -> new AccessEntitlement(
                sampleTicket(), EntitlementType.QUEUE_PRIORITY, null, 2L, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not have rideId");
    }

    @Test
    void shouldRejectQueuePriorityWithNonPositivePriorityLevel() {
        assertThatThrownBy(() -> new AccessEntitlement(
                sampleTicket(), EntitlementType.QUEUE_PRIORITY, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive priorityLevel");
    }

    @Test
    void shouldRejectNullTicket() {
        assertThatThrownBy(() -> new AccessEntitlement(
                null, EntitlementType.ZONE, 1L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ticket is required");
    }

    @Test
    void shouldRejectNullEntitlementType() {
        assertThatThrownBy(() -> new AccessEntitlement(
                sampleTicket(), null, 1L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entitlementType is required");
    }
}
