package com.atpezms.atpezms.ticketing.repository;

import com.atpezms.atpezms.ticketing.entity.Wristband;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Wristband} persistence.
 */
public interface WristbandRepository extends JpaRepository<Wristband, Long> {

    /**
     * Finds a wristband by its unique RFID tag.
     *
     * @param rfidTag the unique RFID tag string
     * @return an Optional containing the matching wristband, or empty if none exists
     */
    Optional<Wristband> findByRfidTag(String rfidTag);
}
