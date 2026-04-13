package com.atpezms.atpezms.park.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atpezms.atpezms.park.entity.ParkConfiguration;
import com.atpezms.atpezms.common.entity.SeasonType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for {@link ParkReferenceService}.
 *
 * <p>Uses the seeded database state from Flyway V001 to verify queries.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ParkReferenceServiceTest {

    @Autowired
    private ParkReferenceService parkReferenceService;

    @Test
    void shouldReturnActiveConfiguration() {
        ParkConfiguration config = parkReferenceService.getActiveConfiguration();
        
        assertThat(config).isNotNull();
        assertThat(config.isActive()).isTrue();
        // Flyway V001 seeds the capacity to 5000
        assertThat(config.getMaxDailyCapacity()).isEqualTo(5000);
    }

    @Test
    void shouldReturnPeakWhenDateFallsInPeakPeriod() {
        // Flyway seeds 2026-04-01 to 2026-04-30 as PEAK
        LocalDate date = LocalDate.of(2026, 4, 15);
        SeasonType type = parkReferenceService.getSeasonTypeForDate(date);
        
        assertThat(type).isEqualTo(SeasonType.PEAK);
    }

    @Test
    void shouldReturnOffPeakWhenDateFallsInOffPeakPeriod() {
        // Flyway seeds 2026-01-01 to 2026-03-31 as OFF_PEAK
        LocalDate date = LocalDate.of(2026, 2, 10);
        SeasonType type = parkReferenceService.getSeasonTypeForDate(date);
        
        assertThat(type).isEqualTo(SeasonType.OFF_PEAK);
    }

    @Test
    void shouldDefaultToOffPeakWhenDateFallsOutsideAnyPeriod() {
        // Flyway seeds periods for 2026. A date in 2027 should default to OFF_PEAK.
        LocalDate date = LocalDate.of(2027, 6, 1);
        SeasonType type = parkReferenceService.getSeasonTypeForDate(date);
        
        assertThat(type).isEqualTo(SeasonType.OFF_PEAK);
    }
}
