package com.atpezms.atpezms.ticketing.repository;

import com.atpezms.atpezms.ticketing.entity.Visitor;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Visitor} persistence.
 *
 * <h2>Why no custom query methods here?</h2>
 * Phase 1.1 only needs to save new visitors and look them up by ID. Both
 * operations are provided by {@link JpaRepository}: {@code save()} for
 * inserts and {@code findById()} for lookups. No additional methods are
 * needed until a future slice requires a specific search (e.g., find by
 * email for duplicate detection).
 *
 * <h2>Important: encrypted columns cannot be queried by equality in JPQL</h2>
 * Because {@code first_name_enc}, {@code last_name_enc}, etc. store AES-GCM
 * ciphertext with a random IV, two rows for visitors with identical names
 * will have different ciphertext values. This means a JPQL query like
 * {@code WHERE v.firstName = :name} would never match -- the Java-side
 * value is plaintext but the database column holds ciphertext.
 *
 * Any search that needs to match on PII fields must load candidates and
 * compare after decryption in Java, or use a deterministic secondary index
 * (e.g., an HMAC of the plaintext). This is a deliberate trade-off documented
 * in {@code IMPLEMENTATION.md} §13.8.
 */
public interface VisitorRepository extends JpaRepository<Visitor, Long> {
}
