package com.atpezms.atpezms.park.repository;

import com.atpezms.atpezms.park.entity.SeasonalPeriod;
import java.time.LocalDate;
import java.util.List;
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
     * <p>Overlapping periods are prevented by the service layer, so this
     * returns at most one result.
     */
    @Query("SELECT s FROM SeasonalPeriod s WHERE :date >= s.startDate AND :date <= s.endDate")
    Optional<SeasonalPeriod> findPeriodContainingDate(@Param("date") LocalDate date);

    /**
     * Returns all periods ordered by start date ascending.
     *
     * <p>Used by {@code GET /api/park/seasonal-periods}.
     */
    List<SeasonalPeriod> findAllByOrderByStartDateAsc();

    /**
     * Detects any existing period whose date range overlaps {@code [from, to]}.
     *
     * <h2>Overlap condition</h2>
     * Two intervals {@code [A,B]} and {@code [C,D]} overlap if and only if
     * {@code A <= D AND B >= C}. In words: "my start is before your end,
     * AND my end is after your start."
     *
     * <h2>excludeId</h2>
     * If {@code excludeId} is non-null, that period is excluded from the check.
     * Phase 2 does not have a period-update flow (periods are immutable), so
     * this is always {@code null} in Phase 2. The parameter is present for
     * forward compatibility.
     *
     * @param from      start date of the proposed period (inclusive)
     * @param to        end date of the proposed period (inclusive)
     * @param excludeId period ID to exclude from the check, or {@code null}
     * @return overlapping periods (empty list means no conflict)
     */
    @Query("""
            SELECT s FROM SeasonalPeriod s
            WHERE s.startDate <= :to
              AND s.endDate   >= :from
              AND (:excludeId IS NULL OR s.id <> :excludeId)
            """)
    List<SeasonalPeriod> findOverlapping(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("excludeId") Long excludeId);
}
