package com.atpezms.atpezms.park.repository;

import com.atpezms.atpezms.park.entity.ParkWriteLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository for the single-row park write lock.
 *
 * <h2>What this repository does</h2>
 * It exposes one meaningful operation: {@link #acquireLock()}, which loads the
 * anchor row (id=1) with a {@code PESSIMISTIC_WRITE} lock mode.
 *
 * <h2>PESSIMISTIC_WRITE explained</h2>
 * In JPA, {@code PESSIMISTIC_WRITE} maps to a {@code SELECT ... FOR UPDATE}
 * statement in SQL. When a transaction acquires this lock, the database prevents
 * any other transaction from also acquiring a write lock on the same row until
 * the first transaction commits or rolls back.
 *
 * <p>This means: if two admin requests arrive concurrently and both try to
 * create a new SeasonalPeriod (or activate a new ParkConfiguration), one of
 * them will block on the lock until the first finishes. Only then does the
 * second proceed -- and it now reads the state left by the first, so the
 * overlap check / uniqueness check sees the updated database.
 *
 * <h2>Why this method must be called inside a transaction</h2>
 * Pessimistic locks are held until the end of the surrounding transaction.
 * If there is no transaction, the lock is released immediately after the
 * SELECT, which defeats the purpose. Services that call {@code acquireLock()}
 * must be annotated {@code @Transactional}.
 */
public interface ParkWriteLockRepository extends JpaRepository<ParkWriteLock, Integer> {

	/**
	 * Acquires the park-wide write lock.
	 *
	 * <p>Must be called inside an active {@code @Transactional} context.
	 * The lock row (id=1) is seeded by V006 and must always be present.
	 *
	 * <p>Callers should use {@code orElseThrow()} on the result:
	 * <pre>
	 *   parkWriteLockRepository.acquireLock()
	 *       .orElseThrow(() -> new IllegalStateException("park_write_lock row missing"));
	 * </pre>
	 * This fails loudly if the row was accidentally deleted, making the
	 * operational problem immediately visible rather than silently losing
	 * the serialization guarantee.
	 *
	 * <p>Why return {@code Optional} instead of the entity directly? Spring
	 * Data's {@code @Query} + {@code @Lock} combination requires the return
	 * type to be {@code Optional<T>} or {@code T}. Returning {@code T} would
	 * throw {@code EmptyResultDataAccessException} (not {@code IllegalStateException})
	 * if the row is absent -- harder to diagnose. The Optional lets callers
	 * provide a meaningful error message.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT l FROM ParkWriteLock l WHERE l.id = 1")
	java.util.Optional<ParkWriteLock> acquireLock();
}
