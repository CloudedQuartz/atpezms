package com.atpezms.atpezms.billing.repository;

import com.atpezms.atpezms.billing.entity.Transaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
	List<Transaction> findByBillIdOrderByCreatedAtAsc(Long billId);
}
