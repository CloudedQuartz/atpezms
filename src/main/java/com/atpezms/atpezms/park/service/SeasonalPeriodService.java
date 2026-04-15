package com.atpezms.atpezms.park.service;

import com.atpezms.atpezms.park.dto.CreateSeasonalPeriodRequest;
import com.atpezms.atpezms.park.dto.SeasonalPeriodResponse;
import com.atpezms.atpezms.park.entity.SeasonalPeriod;
import com.atpezms.atpezms.park.exception.SeasonalPeriodDateConflictException;
import com.atpezms.atpezms.park.exception.SeasonalPeriodInvalidDatesException;
import com.atpezms.atpezms.park.exception.SeasonalPeriodNotFoundException;
import com.atpezms.atpezms.park.repository.ParkWriteLockRepository;
import com.atpezms.atpezms.park.repository.SeasonalPeriodRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for seasonal period management (Phase 2.3).
 *
 * <h2>Why seasonal periods are immutable</h2>
 * Seasonal periods drive ticket pricing. A ticket purchased during a PEAK
 * period was priced at PEAK rates because that period was classified PEAK at
 * purchase time. Allowing retroactive edits to a past period would misrepresent
 * the pricing decision history. If a period was created incorrectly, the
 * correct operation is to delete it and create a new one.
 *
 * <h2>Non-overlap invariant</h2>
 * Two overlapping periods would produce ambiguous pricing: a purchase date that
 * falls in both periods could be classified as PEAK by one query and OFF_PEAK
 * by another. The service enforces non-overlap via the overlap query plus
 * pessimistic locking of the {@code park_write_lock} row (same concurrency
 * strategy as Phase 2.2 ParkConfiguration management).
 *
 * <h2>Concurrency safety</h2>
 * The {@code park_write_lock} anchor row is acquired before the overlap check.
 * This serializes concurrent create/delete requests, so the "check + insert"
 * sequence cannot have a TOCTOU race from another admin request. Note that
 * the pricing hot path (ticket issuance reads {@code findPeriodContainingDate})
 * is read-only and does not need the lock.
 */
@Service
@Transactional(readOnly = true)
public class SeasonalPeriodService {

	private final SeasonalPeriodRepository periodRepository;
	private final ParkWriteLockRepository lockRepository;

	public SeasonalPeriodService(
			SeasonalPeriodRepository periodRepository,
			ParkWriteLockRepository lockRepository) {
		this.periodRepository = periodRepository;
		this.lockRepository = lockRepository;
	}

	/**
	 * Returns all seasonal periods ordered by start date ascending.
	 */
	public List<SeasonalPeriodResponse> listPeriods() {
		return periodRepository.findAllByOrderByStartDateAsc().stream()
				.map(SeasonalPeriodResponse::from)
				.toList();
	}

	/**
	 * Returns a single seasonal period by ID.
	 *
	 * @throws SeasonalPeriodNotFoundException if not found
	 */
	public SeasonalPeriodResponse getPeriod(Long id) {
		return periodRepository.findById(id)
				.map(SeasonalPeriodResponse::from)
				.orElseThrow(() -> new SeasonalPeriodNotFoundException(id));
	}

	/**
	 * Creates a new seasonal period.
	 *
	 * <p>Steps:
	 * <ol>
	 *   <li>Cross-field date validation: {@code endDate >= startDate} (422 on failure).</li>
	 *   <li>Acquire the {@code park_write_lock} to serialize concurrent writes.</li>
	 *   <li>Check for overlapping periods (422 {@code SEASONAL_PERIOD_DATE_CONFLICT}
	 *       if any exist).</li>
	 *   <li>Persist the new period.</li>
	 * </ol>
	 *
	 * @param request validated request
	 * @return the created period DTO
	 * @throws SeasonalPeriodInvalidDatesException if {@code endDate < startDate}
	 * @throws SeasonalPeriodDateConflictException  if the range overlaps an existing period
	 */
	@Transactional
	public SeasonalPeriodResponse createPeriod(CreateSeasonalPeriodRequest request) {
		// Step 1: cross-field validation (not expressible as a single @NotNull/@Past).
		if (request.endDate().isBefore(request.startDate())) {
			throw new SeasonalPeriodInvalidDatesException(request.startDate(), request.endDate());
		}

		// Step 2: acquire the write lock to prevent overlap races.
		lockRepository.acquireLock()
				.orElseThrow(() -> new IllegalStateException(
						"park_write_lock row is missing -- database is in an invalid state"));

		// Step 3: overlap check.
		List<SeasonalPeriod> overlapping = periodRepository.findOverlapping(
				request.startDate(), request.endDate(), null);
		if (!overlapping.isEmpty()) {
			throw new SeasonalPeriodDateConflictException(request.startDate(), request.endDate());
		}

		// Step 4: persist.
		SeasonalPeriod period = new SeasonalPeriod(
				request.startDate(), request.endDate(), request.seasonType());
		SeasonalPeriod saved = periodRepository.save(period);
		return SeasonalPeriodResponse.from(saved);
	}

	/**
	 * Deletes a seasonal period.
	 *
	 * <p>Acquires the {@code park_write_lock} before deleting so concurrent
	 * creates that check for overlaps see a consistent state.
	 *
	 * @param id the period to delete
	 * @throws SeasonalPeriodNotFoundException if the period does not exist
	 */
	@Transactional
	public void deletePeriod(Long id) {
		// Acquire the write lock first so concurrent creates either see the period
		// still present (and reject overlaps), or see it deleted (and proceed).
		lockRepository.acquireLock()
				.orElseThrow(() -> new IllegalStateException(
						"park_write_lock row is missing -- database is in an invalid state"));

		SeasonalPeriod period = periodRepository.findById(id)
				.orElseThrow(() -> new SeasonalPeriodNotFoundException(id));

		periodRepository.delete(period);
	}
}
