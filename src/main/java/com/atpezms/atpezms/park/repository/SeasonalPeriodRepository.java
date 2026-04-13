package com.atpezms.atpezms.park.repository;

import com.atpezms.atpezms.park.entity.SeasonalPeriod;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link SeasonalPeriod} persistence.
 */
public interface SeasonalPeriodRepository extends JpaRepository<SeasonalPeriod, Long> {

    /**
     * Finds the seasonal period that includes the given date.
     *
     * <p>A date is included in a period if it is greater than or equal to the
     * start date, and less than or equal to the end date.
     * Overlapping periods are prevented by business rules when they are created,
     * so this should return at most one result.
     *
     * @param date the date to check
     * @return an Optional containing the matching period, or empty if no period covers the date
     */
    @Query("SELECT s FROM SeasonalPeriod s WHERE :date >= s.startDate AND :date <= s.endDate")
    Optional<SeasonalPeriod> findPeriodContainingDate(@Param("date") LocalDate date);
}
