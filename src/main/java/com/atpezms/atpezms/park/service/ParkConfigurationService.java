package com.atpezms.atpezms.park.service;

import com.atpezms.atpezms.park.dto.CreateParkConfigurationRequest;
import com.atpezms.atpezms.park.dto.ParkConfigurationResponse;
import com.atpezms.atpezms.park.entity.ParkConfiguration;
import com.atpezms.atpezms.park.exception.CapacityReductionConflictException;
import com.atpezms.atpezms.park.exception.NoActiveParkConfigurationException;
import com.atpezms.atpezms.park.repository.ParkConfigurationRepository;
import com.atpezms.atpezms.park.repository.ParkWriteLockRepository;
import com.atpezms.atpezms.ticketing.entity.ParkDayCapacity;
import com.atpezms.atpezms.ticketing.repository.ParkDayCapacityRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for park configuration management (Phase 2.2).
 *
 * <h2>Activate-on-create pattern</h2>
 * Creating a new configuration always activates it immediately. The endpoint
 * does not allow creating an inactive configuration (that would be a limbo state).
 * The old active configuration is deactivated in the same transaction, so there
 * is never a gap where zero configurations are active, and never a moment where
 * two are active simultaneously.
 *
 * <h2>Why this service calls into the Ticketing repository</h2>
 * {@code ParkDayCapacity} lives in the Ticketing context because it was
 * introduced as part of ticket issuance (Phase 1). However, when the Park
 * configuration changes, we must propagate the new capacity to those rows.
 * This is a legitimate intra-monolith, cross-context write required by the
 * design decision in {@code PHASE_02_PARK_DESIGN.md §2.5}.
 *
 * The cross-context rule ({@code DESIGN.md §6.1}) says contexts communicate
 * via service calls. Here, Park's service directly uses the Ticketing
 * repository rather than calling a Ticketing service method. This is a
 * pragmatic exception documented here: calling {@code VisitService} for a
 * bulk-update would couple the two services unnecessarily for what is simply
 * a data propagation operation with no business logic in Ticketing. When the
 * Ticketing service gains a proper "update capacity limit" operation, this
 * call site should be updated.
 *
 * <h2>Concurrency safety</h2>
 * All writes acquire the {@code park_write_lock} row before proceeding.
 * This serializes concurrent config-activation requests so only one can
 * deactivate-then-create at a time.
 */
@Service
@Transactional(readOnly = true)
public class ParkConfigurationService {

	private static final Logger log = LoggerFactory.getLogger(ParkConfigurationService.class);

	private final ParkConfigurationRepository configRepository;
	private final ParkDayCapacityRepository dayCapacityRepository;
	private final ParkWriteLockRepository lockRepository;
	private final EntityManager entityManager;
	private final Clock clock;

	public ParkConfigurationService(
			ParkConfigurationRepository configRepository,
			ParkDayCapacityRepository dayCapacityRepository,
			ParkWriteLockRepository lockRepository,
			EntityManager entityManager,
			Clock clock) {
		this.configRepository = configRepository;
		this.dayCapacityRepository = dayCapacityRepository;
		this.lockRepository = lockRepository;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	/**
	 * Returns all park configurations, newest first.
	 */
	public List<ParkConfigurationResponse> listConfigurations() {
		return configRepository.findAllByOrderByIdDesc().stream()
				.map(ParkConfigurationResponse::from)
				.toList();
	}

	/**
	 * Returns the currently active park configuration.
	 *
	 * @throws NoActiveParkConfigurationException if no active configuration exists
	 */
	public ParkConfigurationResponse getActiveConfiguration() {
		return configRepository.findByActiveTrue()
				.map(ParkConfigurationResponse::from)
				.orElseThrow(NoActiveParkConfigurationException::new);
	}

	/**
	 * Creates a new park configuration and atomically activates it.
	 *
	 * <p>Steps (all within one {@code @Transactional} boundary):
	 * <ol>
	 *   <li>Acquire the {@code park_write_lock} pessimistic write lock to
	 *       serialize concurrent activation requests.</li>
	 *   <li>Check {@code park_day_capacity} for future dates whose
	 *       {@code issuedCount} would exceed the new max. Reject if any exist.</li>
	 *   <li>Deactivate the current active configuration via a JPQL bulk update.</li>
	 *   <li>Persist the new configuration with {@code active = true}.</li>
	 *   <li>Bulk-update future {@code park_day_capacity} rows to the new max.</li>
	 * </ol>
	 *
	 * @param request validated request with the new {@code maxDailyCapacity}
	 * @return the newly created active configuration DTO
	 * @throws CapacityReductionConflictException if the new capacity is less than
	 *         an already-issued count for a future date
	 */
	@Transactional
	public ParkConfigurationResponse createAndActivate(CreateParkConfigurationRequest request) {
		// Step 1: acquire the write lock (serializes concurrent activations).
		lockRepository.acquireLock()
				.orElseThrow(() -> new IllegalStateException(
						"park_write_lock row is missing -- database is in an invalid state"));

		Instant now = Instant.now(clock);
		LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
		int newMax = request.maxDailyCapacity();

		// Step 2: conflict check -- any future date with issued_count > new max?
		List<ParkDayCapacity> conflicts = dayCapacityRepository
				.findConflictingFutureDates(today, newMax);
		if (!conflicts.isEmpty()) {
			ParkDayCapacity earliest = conflicts.get(0);
			throw new CapacityReductionConflictException(
					earliest.getVisitDate(), newMax, earliest.getIssuedCount());
		}

		// Step 3: deactivate the current active config.
		int deactivated = configRepository.deactivateAll(now);
		// In a healthy system, exactly one row should be deactivated.
		// Zero means the system had no active config (invariant violation);
		// more than one means the invariant was already broken before we started.
		// Log a warning so the anomaly is visible in application logs.
		if (deactivated != 1) {
			log.warn("deactivateAll affected {} rows (expected 1); possible configuration invariant violation", deactivated);
		}

		// Step 4: clear the first-level cache after the JPQL bulk update.
		//
		// JPQL bulk updates (UPDATE ... SET ...) bypass the JPA persistence context:
		// they go directly to the database without touching managed entity instances.
		// This means the old ParkConfiguration entity (with active=true) is still
		// in the first-level cache. If we were to re-read it before clearing, JPA
		// would serve the stale cached version rather than the updated database state.
		//
		// entityManager.clear() evicts all managed entities from the first-level cache,
		// forcing the next read to go to the database. This is safe here because all
		// the domain logic in this method is complete -- there are no further mutations
		// of already-managed entities after this point.
		//
		// Reference: IMPLEMENTATION.md §6.1 (Bulk Updates And Auditing).
		entityManager.clear();

		// Step 5: persist the new active configuration.
		ParkConfiguration newConfig = new ParkConfiguration(true, newMax);
		ParkConfiguration saved = configRepository.save(newConfig);

		// Step 6: propagate new max to all existing future park_day_capacity rows.
		dayCapacityRepository.updateMaxCapacityFromToday(newMax, today, now);
		// Clear again after the second bulk update (dayCapacity rows may also be cached).
		entityManager.clear();

		return ParkConfigurationResponse.from(saved);
	}
}
