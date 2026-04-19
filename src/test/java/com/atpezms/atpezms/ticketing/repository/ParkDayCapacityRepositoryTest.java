package com.atpezms.atpezms.ticketing.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atpezms.atpezms.ticketing.entity.ParkDayCapacity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TransactionRequiredException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.IllegalTransactionStateException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class ParkDayCapacityRepositoryTest {

    @Autowired
    private ParkDayCapacityRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanUp() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> repository.deleteAll());
    }

    @Test
    @Transactional
    void shouldIncrementWhenCapacityAvailable() {
        LocalDate date = LocalDate.of(2099, 4, 20);
        repository.saveAndFlush(new ParkDayCapacity(date, 2));

        entityManager.clear();
        ParkDayCapacity beforeRow = repository.findByVisitDate(date).orElseThrow();

        Instant t1 = beforeRow.getUpdatedAt().plusSeconds(1);
        Instant t2 = beforeRow.getUpdatedAt().plusSeconds(2);
        Instant t3 = beforeRow.getUpdatedAt().plusSeconds(3);

        int first = repository.incrementIfCapacityAvailable(date, t1);
        int second = repository.incrementIfCapacityAvailable(date, t2);
        int third = repository.incrementIfCapacityAvailable(date, t3);

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(1);
        assertThat(third).isEqualTo(0); // sold out at max=2

        // JPQL bulk updates bypass the first-level cache. Clear the persistence
        // context before reading back to ensure we see the DB value.
        entityManager.clear();

        ParkDayCapacity row = repository.findByVisitDate(date).orElseThrow();
        assertThat(row.getIssuedCount()).isEqualTo(2);

        // Bulk updates bypass auditing listeners, so the query must maintain updatedAt explicitly.
        assertThat(row.getUpdatedAt()).isEqualTo(t2);
    }

    @Test
    void shouldRollbackIncrementWhenTransactionRollsBack() {
        LocalDate date = LocalDate.of(2099, 4, 21);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        tx.executeWithoutResult(status -> repository.saveAndFlush(new ParkDayCapacity(date, 1)));

        try {
            Instant initialUpdatedAt = tx.execute(status -> {
                entityManager.clear();
                return repository.findByVisitDate(date).orElseThrow().getUpdatedAt();
            });
            assertThat(initialUpdatedAt).isNotNull();

            tx.executeWithoutResult(status -> {
                int updated = repository.incrementIfCapacityAvailable(date, initialUpdatedAt.plusSeconds(5));
                assertThat(updated).isEqualTo(1);
                status.setRollbackOnly();
            });

            ParkDayCapacity afterRollback = tx.execute(status -> {
                entityManager.clear();
                return repository.findByVisitDate(date).orElseThrow();
            });

            assertThat(afterRollback.getIssuedCount()).isZero();
            assertThat(afterRollback.getUpdatedAt()).isEqualTo(initialUpdatedAt);
        } finally {
            tx.executeWithoutResult(status -> repository.findByVisitDate(date).ifPresent(repository::delete));
        }
    }

    @Test
    void shouldNotOversellUnderConcurrency() throws Exception {
        LocalDate date = LocalDate.of(2099, 4, 22);
        int maxCapacity = 5;
        int attempts = 25;
        Duration timeout = Duration.ofSeconds(10);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        tx.executeWithoutResult(status -> repository.saveAndFlush(new ParkDayCapacity(date, maxCapacity)));

        // Use one thread per attempt so all tasks can block on the latch and then start together.
        int poolSize = attempts;
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);

        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>(attempts);

        try {
            for (int i = 0; i < attempts; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    boolean started = start.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
                    if (!started) {
                        throw new IllegalStateException("Timed out waiting for concurrent start");
                    }
                    return tx.execute(status -> repository.incrementIfCapacityAvailable(date, Instant.now()));
                }));
            }

            assertThat(ready.await(timeout.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            start.countDown();

            int successes = 0;
            for (Future<Integer> f : futures) {
                successes += f.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }

            assertThat(successes).isEqualTo(maxCapacity);

            Integer issued = tx.execute(status -> {
                entityManager.clear();
                return repository.findByVisitDate(date).orElseThrow().getIssuedCount();
            });
            assertThat(issued).isEqualTo(maxCapacity);
        } finally {
            executor.shutdownNow();
            tx.executeWithoutResult(status -> repository.findByVisitDate(date).ifPresent(repository::delete));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRequireExistingTransaction() {
        assertThatThrownBy(() -> repository.incrementIfCapacityAvailable(LocalDate.of(2099, 4, 20), Instant.now()))
                .isInstanceOfAny(IllegalTransactionStateException.class, TransactionRequiredException.class);
    }
}
