package com.atpezms.atpezms.billing.repository;

import com.atpezms.atpezms.billing.entity.Bill;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillRepository extends JpaRepository<Bill, Long> {

	/**
	 * Pessimistic write lock prevents concurrent auto-creation of bills
	 * for the same visitId (TOCTOU protection).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT b FROM Bill b WHERE b.visitId = :visitId")
	Optional<Bill> findByVisitIdForUpdate(@Param("visitId") Long visitId);

	Optional<Bill> findByVisitId(Long visitId);
}
