package com.atpezms.atpezms.park.repository;

import com.atpezms.atpezms.park.entity.ParkConfiguration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ParkConfiguration} persistence.
 *
 * <p>Phase 1 only needs to read the single active configuration to
 * enforce capacity limits during ticket issuance.
 */
public interface ParkConfigurationRepository extends JpaRepository<ParkConfiguration, Long> {
    
    /**
     * Finds the currently active park configuration.
     * The business rule states there must be exactly one active configuration.
     *
     * @return an Optional containing the active configuration, or empty if none
     */
    Optional<ParkConfiguration> findByActiveTrue();
}
