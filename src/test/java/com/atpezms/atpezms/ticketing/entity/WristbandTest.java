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
    void shouldReturnToStockFromActive() {
        Wristband w = new Wristband("123");
        w.activate();
        w.returnToStock();
        assertThat(w.getStatus()).isEqualTo(WristbandStatus.IN_STOCK);
    }

    @Test
    void shouldNotReturnToStockIfNotActive() {
        Wristband w = new Wristband("123"); // IN_STOCK
        
        assertThatThrownBy(w::returnToStock)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot return");
    }

    @Test
    void shouldDeactivateFromAnyState() {
        Wristband w = new Wristband("123");
        w.deactivate();
        assertThat(w.getStatus()).isEqualTo(WristbandStatus.DEACTIVATED);
        
        assertThatThrownBy(w::activate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(w::returnToStock).isInstanceOf(IllegalStateException.class);
    }
}
