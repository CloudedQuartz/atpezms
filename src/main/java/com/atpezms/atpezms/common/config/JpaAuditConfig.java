package com.atpezms.atpezms.common.config;

import java.time.Instant;
import java.util.Optional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.context.annotation.Bean;

/**
 * Enables JPA auditing so BaseEntity can populate createdAt/updatedAt.
 *
 * Why this exists:
 * - The BaseEntity fields use @CreatedDate / @LastModifiedDate.
 * - Those annotations do nothing unless auditing is enabled.
 *
 * We also provide an explicit DateTimeProvider that returns Instant.now() so
 * the audit timestamps are unambiguously UTC-based Instants.
 */
@Configuration
@EnableJpaAuditing(
		modifyOnCreate = true,
		dateTimeProviderRef = "auditDateTimeProvider"
)
public class JpaAuditConfig {
	@Bean
	DateTimeProvider auditDateTimeProvider() {
		return () -> Optional.of(Instant.now());
	}
}
