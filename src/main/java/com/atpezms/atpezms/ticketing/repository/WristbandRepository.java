package com.atpezms.atpezms.ticketing.repository;

import com.atpezms.atpezms.ticketing.entity.Wristband;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	/**
	 * Finds a wristband by RFID tag and locks it for update.
	 *
	 * <p>Used during ticket issuance so two concurrent requests cannot both see
	 * the same IN_STOCK wristband and activate it.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT w FROM Wristband w WHERE w.rfidTag = :rfidTag")
	Optional<Wristband> findByRfidTagForUpdate(@Param("rfidTag") String rfidTag);
}
