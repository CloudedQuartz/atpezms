package com.atpezms.atpezms.ticketing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.atpezms.atpezms.common.entity.SeasonType;
import com.atpezms.atpezms.ticketing.entity.AgeGroup;
import com.atpezms.atpezms.ticketing.entity.DayType;
import com.atpezms.atpezms.ticketing.entity.PassType;
import com.atpezms.atpezms.ticketing.entity.PassTypePrice;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for the price matrix repository.
 *
 * <p>Uses the seeded database state from Flyway V001 to verify queries.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PassTypePriceRepositoryTest {

    @Autowired
    private PassTypePriceRepository passTypePriceRepository;

    @Autowired
    private PassTypeRepository passTypeRepository;

    @Test
    void shouldFindPriceForValidCombination() {
        // V001 seeds all pass types; look up by code, not by numeric ID.
        // Verifies the price: SINGLE_DAY + CHILD + WEEKDAY + OFF_PEAK = 150000 LKR
        PassType passType = passTypeRepository.findByCode(com.atpezms.atpezms.ticketing.entity.PassTypeCode.SINGLE_DAY).orElseThrow();

        Optional<PassTypePrice> priceOpt = passTypePriceRepository
                .findByPassTypeAndAgeGroupAndDayTypeAndSeasonType(
                        passType, AgeGroup.CHILD, DayType.WEEKDAY, SeasonType.OFF_PEAK);

        assertThat(priceOpt).isPresent();
        PassTypePrice price = priceOpt.get();
        assertThat(price.getPriceCents()).isEqualTo(150000);
        assertThat(price.getCurrency()).isEqualTo("LKR");
    }

    @Test
    void shouldReturnEmptyForMissingCombination() {
        // Find a pass type that doesn't have prices or just test an impossible comb
        PassType passType = passTypeRepository.findByCode(com.atpezms.atpezms.ticketing.entity.PassTypeCode.SINGLE_DAY).orElseThrow();
        // Delete all prices for it temporarily in the transaction to test empty optional
        passTypePriceRepository.deleteAll();

        Optional<PassTypePrice> priceOpt = passTypePriceRepository
                .findByPassTypeAndAgeGroupAndDayTypeAndSeasonType(
                        passType, AgeGroup.CHILD, DayType.WEEKDAY, SeasonType.OFF_PEAK);

        assertThat(priceOpt).isEmpty();
    }
}
