package com.atpezms.atpezms.ticketing.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WristbandTest {

    @Test
    void shouldCreateNewWristbandInStock() {
        Wristband w = new Wristband("123-ABC");
        assertThat(w.getRfidTag()).isEqualTo("123-ABC");
        assertThat(w.getStatus()).isEqualTo(WristbandStatus.IN_STOCK);
    }

    @Test
    void shouldRejectBlankTag() {
        assertThatThrownBy(() -> new Wristband("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldActivateFromInStock() {
        Wristband w = new Wristband("123");
        w.activate();
        assertThat(w.getStatus()).isEqualTo(WristbandStatus.ACTIVE);
    }

    @Test
    void shouldNotActivateIfNotInStock() {
        Wristband w = new Wristband("123");
        w.activate(); // now ACTIVE
        assertThatThrownBy(w::activate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot activate");
    }

    @Test
    void shouldMakeInactiveFromActive() {
        Wristband w = new Wristband("123");
        w.activate();
        w.makeInactive();
        assertThat(w.getStatus()).isEqualTo(WristbandStatus.INACTIVE);
    }

    @Test
    void shouldNotMakeInactiveIfNotActive() {
        Wristband w = new Wristband("123"); // IN_STOCK
        assertThatThrownBy(w::makeInactive)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot make a wristband inactive");
    }

    @Test
    void shouldDeactivateFromAnyState() {
        Wristband w = new Wristband("123");
        w.deactivate();
        assertThat(w.getStatus()).isEqualTo(WristbandStatus.DEACTIVATED);

        assertThatThrownBy(w::activate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(w::makeInactive).isInstanceOf(IllegalStateException.class);
    }
}
