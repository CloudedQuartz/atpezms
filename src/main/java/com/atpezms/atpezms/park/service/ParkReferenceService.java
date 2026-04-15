package com.atpezms.atpezms.park.service;

import com.atpezms.atpezms.common.entity.SeasonType;
import com.atpezms.atpezms.park.entity.SeasonalPeriod;
import com.atpezms.atpezms.park.repository.ParkConfigurationRepository;
import com.atpezms.atpezms.park.repository.SeasonalPeriodRepository;
import com.atpezms.atpezms.park.repository.ZoneRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for Park reference data read queries.
 *
	 * <p>Phase 1 introduces this service "Just In Time" so the Ticketing context
	 * can query the active park capacity and the season type for a given date
	 * without directly accessing the Park repositories.
	 * (Rule: Contexts communicate via services, not cross-context repository access).
 *
	 * <p>All methods are read-only.
	 */
@Service
@Transactional(readOnly = true)
public class ParkReferenceService {

    private final ParkConfigurationRepository configRepository;
    private final SeasonalPeriodRepository seasonRepository;
    private final ZoneRepository zoneRepository;

    public ParkReferenceService(
            ParkConfigurationRepository configRepository,
            SeasonalPeriodRepository seasonRepository,
            ZoneRepository zoneRepository) {
        this.configRepository = configRepository;
        this.seasonRepository = seasonRepository;
        this.zoneRepository = zoneRepository;
    }

	/**
	 * Gets the currently active maximum daily visitor capacity.
	 *
	 * <p>This returns a scalar rather than exposing Park JPA entities across the
	 * context boundary.
	 */
	public int getActiveMaxDailyCapacity() {
		return configRepository.findByActiveTrue()
				.orElseThrow(() -> new IllegalStateException("No active park configuration found"))
				.getMaxDailyCapacity();
	}

    /**
     * Determines the season type for a given date.
     *
     * <p>If the date falls within a configured seasonal period, that period's
     * season type is returned. If it does not fall within any configured period,
     * it defaults to OFF_PEAK (per the pricing algorithm spec).
     *
     * @param date the date to check
     * @return the season type (PEAK or OFF_PEAK)
     */
    public SeasonType getSeasonTypeForDate(LocalDate date) {
        return seasonRepository.findPeriodContainingDate(date)
                .map(SeasonalPeriod::getSeasonType)
                .orElse(SeasonType.OFF_PEAK);
    }

	/**
	 * Lists all seeded park zone IDs.
	 *
	 * <p>Ticketing stores cross-context references by ID (DESIGN.md §6.2), so this
	 * method returns scalar IDs instead of Park entity instances.
	 */
	public List<Long> listZoneIds() {
		return zoneRepository.findAllByOrderByIdAsc()
				.stream()
				.map(z -> Objects.requireNonNull(z.getId(), "Zone id must be present"))
				.toList();
	}
}
