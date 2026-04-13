package com.atpezms.atpezms.ticketing.repository;

import com.atpezms.atpezms.ticketing.entity.PassType;
import com.atpezms.atpezms.ticketing.entity.PassTypeCode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read-only Spring Data repository for pass type configuration.
 *
 * Spring Data generates the implementation at startup. The method name
 * {@code findByActiveTrueOrderByCodeAsc} is parsed by Spring Data's
 * method-name derivation engine: "findBy" starts a query, "ActiveTrue" adds
 * a WHERE clause on the boolean column, and "OrderByCodeAsc" appends an ORDER BY.
 * No @Query annotation is needed because the query is simple enough to express
 * in the method name.
 */
public interface PassTypeRepository extends JpaRepository<PassType, Long> {
	List<PassType> findByActiveTrueOrderByCodeAsc();
    Optional<PassType> findByCode(PassTypeCode code);
}
