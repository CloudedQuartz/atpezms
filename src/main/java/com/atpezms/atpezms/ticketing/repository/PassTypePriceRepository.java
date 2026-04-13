package com.atpezms.atpezms.ticketing.repository;

import com.atpezms.atpezms.common.entity.SeasonType;
import com.atpezms.atpezms.ticketing.entity.AgeGroup;
import com.atpezms.atpezms.ticketing.entity.DayType;
import com.atpezms.atpezms.ticketing.entity.PassType;
import com.atpezms.atpezms.ticketing.entity.PassTypePrice;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link PassTypePrice} persistence.
 */
public interface PassTypePriceRepository extends JpaRepository<PassTypePrice, Long> {

    /**
     * Looks up the exact price for a given combination.
     *
     * <p>Because there is a database-level UNIQUE constraint on
     * (pass_type_id, age_group, day_type, season_type), this query will
     * return at most one row.
     *
     * @param passType the pass type
     * @param ageGroup the visitor's age group
     * @param dayType whether the visit is on a weekday or weekend
     * @param seasonType whether the visit date is in a peak or off-peak period
     * @return an Optional containing the matching price row, or empty if none exists
     */
    Optional<PassTypePrice> findByPassTypeAndAgeGroupAndDayTypeAndSeasonType(
            PassType passType, AgeGroup ageGroup, DayType dayType, SeasonType seasonType);
}
