package com.atpezms.atpezms.ticketing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atpezms.atpezms.common.exception.BusinessRuleViolationException;
import com.atpezms.atpezms.ticketing.dto.IssueVisitRequest;
import com.atpezms.atpezms.ticketing.entity.ParkDayCapacity;
import com.atpezms.atpezms.ticketing.entity.PassTypeCode;
import com.atpezms.atpezms.ticketing.entity.Visitor;
import com.atpezms.atpezms.ticketing.repository.ParkDayCapacityRepository;
import com.atpezms.atpezms.ticketing.repository.PassTypeRepository;
import com.atpezms.atpezms.ticketing.repository.VisitorRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Non-transactional tests for multi-day capacity reservation rollback.
 *
 * <p>These tests must NOT be wrapped in a @Transactional class-level annotation because
 * they rely on committed state to verify that Spring rolls back issued_count increments
 * when a later day in the multi-day window is sold out. A @Transactional test would roll
 * back everything at test teardown, masking whether the service's own transaction
 * boundaries behaved correctly.
 *
 * <p>Far-future dates (year 2099) are used to avoid conflicts with other tests that use
 * LocalDate.now() as their visit date.
 */
@SpringBootTest
@ActiveProfiles("test")
class VisitServiceCapacityRollbackTest {

    // Year 2099 dates are unreachable by any other test using LocalDate.now()
    private static final LocalDate DAY_0 = LocalDate.of(2099, 6, 1);
    private static final LocalDate DAY_1 = DAY_0.plusDays(1); // will be sold out in the test
    private static final LocalDate DAY_2 = DAY_0.plusDays(2);

    @Autowired
    private VisitService visitService;
    @Autowired
    private VisitorRepository visitorRepository;
    @Autowired
    private PassTypeRepository passTypeRepository;
    @Autowired
    private ParkDayCapacityRepository parkDayCapacityRepository;
    @Autowired
    private PlatformTransactionManager txManager;

    /**
     * Verifies the all-or-nothing capacity rollback rule from PHASE_01_TICKETING_DESIGN.md §6.1:
     *
     * <p>If any day in the multi-day validity window is sold out, the entire issuance transaction
     * rolls back, meaning no capacity is consumed for any day — including days that were
     * successfully incremented before the sold-out day was encountered.
     *
     * <p>Setup: DAY_1 (the middle day) is manually set to sold-out (issued_count == max_capacity).
     * A 3-day issuance for [DAY_0, DAY_1, DAY_2] is attempted. The service increments DAY_0
     * successfully, then hits DAY_1 and throws CAPACITY_EXCEEDED. Spring rolls back the outer
     * transaction, reverting DAY_0's increment. We then verify DAY_0's issued_count is 0.
     */
    @Test
    void shouldRollBackAllCapacityReservationsIfAnyDayInWindowIsSoldOut() {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        // Create a visitor and sell out DAY_1, all in separate committed transactions.
        Visitor visitor = tx.execute(status ->
                visitorRepository.save(new Visitor(
                        "Capacity", "Rollback", null, null,
                        LocalDate.of(1990, 1, 1), 170
                ))
        );

        tx.executeWithoutResult(status -> {
            // maxCapacity=1 means the very first increment fills it to sold-out.
            ParkDayCapacity soldOutDay = new ParkDayCapacity(DAY_1, 1);
            parkDayCapacityRepository.saveAndFlush(soldOutDay);
            // Fill the slot: issued_count becomes 1 == max_capacity -> sold out.
            parkDayCapacityRepository.incrementIfCapacityAvailable(DAY_1, Instant.now());
        });

        try {
            var passType = passTypeRepository.findByCode(PassTypeCode.MULTI_DAY).orElseThrow();

            // Issuance for [DAY_0, DAY_1, DAY_2]: DAY_0 increments, DAY_1 is sold out -> rollback.
            assertThatThrownBy(() -> visitService.issueTicketAndStartVisit(
                    new IssueVisitRequest(visitor.getId(), "ROLLBACK-RFID-001", passType.getId(), DAY_0)
            ))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "CAPACITY_EXCEEDED");

            // DAY_0 was incremented inside the outer transaction, which then rolled back.
            // The capacity row for DAY_0 may or may not exist (it is created in a REQUIRES_NEW
            // inner transaction before the retry). If it exists, issued_count must be 0.
            parkDayCapacityRepository.findByVisitDate(DAY_0).ifPresent(cap ->
                    assertThat(cap.getIssuedCount())
                            .as("DAY_0 issued_count should be 0 after rollback")
                            .isZero()
            );
        } finally {
            // Clean up all rows created in this test regardless of outcome.
            tx.executeWithoutResult(status -> {
                parkDayCapacityRepository.findByVisitDate(DAY_0).ifPresent(parkDayCapacityRepository::delete);
                parkDayCapacityRepository.findByVisitDate(DAY_1).ifPresent(parkDayCapacityRepository::delete);
                parkDayCapacityRepository.findByVisitDate(DAY_2).ifPresent(parkDayCapacityRepository::delete);
                visitorRepository.delete(visitor);
            });
        }
    }
}
