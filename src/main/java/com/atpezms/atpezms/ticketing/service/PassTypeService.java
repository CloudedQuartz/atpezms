package com.atpezms.atpezms.ticketing.service;

import com.atpezms.atpezms.ticketing.dto.PassTypeResponse;
import com.atpezms.atpezms.ticketing.repository.PassTypeRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for pass type configuration queries.
 *
 * {@code @Service} registers this class as a Spring bean. Because services are
 * concrete classes (not interface + impl pairs) per our conventions, Spring
 * injects the repository directly via constructor injection. Constructor
 * injection is preferred over field injection because it makes dependencies
 * explicit and the class testable without a Spring context.
 *
 * {@code @Transactional(readOnly = true)} opens a read-only transaction for the
 * duration of the method. "Read-only" is a hint to the underlying JPA provider
 * (Hibernate) and the database driver: Hibernate can skip dirty-checking at
 * flush time, and some databases can route the query to a read replica. It does
 * not enforce immutability -- it is a performance hint, not a safety boundary.
 */
@Service
public class PassTypeService {
	private final PassTypeRepository passTypeRepository;

	public PassTypeService(PassTypeRepository passTypeRepository) {
		this.passTypeRepository = passTypeRepository;
	}

	@Transactional(readOnly = true)
	public List<PassTypeResponse> listActivePassTypes() {
		return passTypeRepository.findByActiveTrueOrderByCodeAsc().stream()
				.map(PassTypeResponse::from)
				.toList();
	}
}
