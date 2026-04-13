package com.atpezms.atpezms.common.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter that transparently encrypts and decrypts PII String
 * fields at the persistence boundary.
 *
 * <h2>Why a JPA AttributeConverter?</h2>
 * JPA's {@code AttributeConverter<X, Y>} is an interface with two methods:
 * {@code convertToDatabaseColumn} (called on every INSERT/UPDATE) and
 * {@code convertToEntityAttribute} (called on every SELECT). Implementing it
 * here means encryption and decryption happen automatically -- no other code
 * needs to call encrypt/decrypt explicitly. The rest of the application always
 * sees plain String values; only the database column ever holds ciphertext.
 *
 * <h2>Why AES-GCM?</h2>
 * AES-GCM is an <em>authenticated encryption with associated data</em> (AEAD)
 * scheme. It provides two guarantees simultaneously:
 * <ul>
 *   <li><strong>Confidentiality</strong> -- the plaintext cannot be read without
 *       the key (same as AES-CBC).</li>
 *   <li><strong>Integrity / tamper detection</strong> -- GCM appends an
 *       authentication tag (16 bytes by default). Decryption fails loudly if
 *       even a single bit of the ciphertext or tag has been altered. AES-CBC
 *       does <em>not</em> provide this: a corrupted ciphertext would silently
 *       produce garbage plaintext.</li>
 * </ul>
 *
 * <h2>Random IV per encryption call</h2>
 * The IV (Initialization Vector, also called a nonce in GCM) must be unique
 * for every (key, plaintext) pair. Reusing an IV with the same key in GCM
 * is a catastrophic security failure: it reveals the keystream and allows an
 * attacker to recover plaintext. A fresh 12-byte random IV per call makes
 * each encryption independent. As a bonus, encrypting the same plaintext twice
 * produces different ciphertexts, preventing dictionary attacks against the
 * database column.
 *
 * <h2>Stored format</h2>
 * The database column holds: {@code Base64( IV || ciphertext+tag )}.
 * Java's JCE Cipher returns ciphertext and the GCM authentication tag as a
 * single contiguous byte array from {@code doFinal}. We prepend the 12-byte
 * IV, then Base64-encode the whole thing. On decryption we peel off the first
 * 12 bytes as the IV and pass the remainder to {@code doFinal}; the JCE
 * verifies the tag automatically and throws if it does not match.
 *
 * Column sizing note: {@code ceil((12 + plaintextLen + 16) * 4.0 / 3)} gives
 * the Base64 length. For a 100-char name: ceil(128 * 4/3) = 172 chars. The
 * schema uses VARCHAR(512) for all encrypted columns, which comfortably
 * accommodates any realistic PII value.
 *
 * <h2>Spring DI inside a JPA converter</h2>
 * The {@code @Component} annotation makes this a Spring-managed bean. Spring
 * Boot configures Hibernate with
 * {@code hibernate.resource.beans.container = SpringBeanContainer}, which
 * tells Hibernate to resolve {@code AttributeConverter} instances from the
 * Spring {@code ApplicationContext} rather than instantiating them directly.
 * This is why the {@code @Value}-injected key works in the constructor -- the
 * converter is wired by Spring before Hibernate starts using it.
 *
 * <h2>autoApply = false</h2>
 * {@code @Converter(autoApply = false)} means the converter is <em>opt-in</em>.
 * A field must declare {@code @Convert(converter = StringEncryptionConverter.class)}
 * explicitly. If {@code autoApply = true}, Hibernate would encrypt every
 * {@code String} field in the entire application -- catastrophically wrong.
 */
@Component
@Converter(autoApply = false)
public class StringEncryptionConverter implements AttributeConverter<String, String> {

    /**
     * AES in Galois/Counter Mode with no block padding.
     * GCM is a stream-cipher mode, so the output is the same length as the
     * input (plus the authentication tag), with no padding required.
     */
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    /**
     * NIST SP 800-38D recommends 96-bit (12-byte) IVs for GCM.
     * Longer IVs are hashed down anyway; shorter ones reduce security margins.
     */
    private static final int IV_LENGTH_BYTES = 12;

    /**
     * Authentication tag length in bits. 128 is the maximum and strongest
     * option. Shorter tags (e.g. 96 bits) are permitted but offer less
     * forgery resistance.
     */
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec secretKey;

    /**
     * {@code SecureRandom} is thread-safe. A single shared instance is
     * preferable to creating a new one per call (which could be expensive
     * on some platforms that seed from /dev/random).
     */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * @param encryptionKeyBase64 Base64-encoded 256-bit (32-byte) AES key.
     *     Injected from the {@code atpezms.encryption.key} application property.
     *     In production this property should come from an environment variable
     *     or a secrets manager -- never from a committed file.
     *     The constructor validates the length so any misconfiguration is caught
     *     at startup rather than silently encrypting with the wrong key size.
     *
     *     <strong>Never log this value.</strong>
     */
    public StringEncryptionConverter(
            @Value("${atpezms.encryption.key}") String encryptionKeyBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
        if (keyBytes.length != 32) {
            // Fail fast: a bad key length means every subsequent encryption would
            // be wrong. Throwing here surfaces the misconfiguration immediately.
            throw new IllegalArgumentException(
                    "atpezms.encryption.key must decode to exactly 32 bytes (256-bit AES). "
                            + "Got " + keyBytes.length + " bytes.");
        }
        // SecretKeySpec wraps the raw bytes into a JCE key object understood
        // by the Cipher API. "AES" is the algorithm family name.
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Called by Hibernate before every INSERT and UPDATE.
     *
     * <p>Null in → null out: nullable columns in the schema (email, phone) store
     * NULL rather than ciphertext for absent values, which makes it obvious from
     * the database that the field was not provided.
     *
     * @param plaintext the Java-side plaintext String value
     * @return Base64-encoded {@code IV || ciphertext+tag}, or null if plaintext is null
     */
    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            // 1. Generate a fresh random IV for this encryption call.
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            // 2. Initialise the cipher in encryption mode with the key and IV.
            //    GCMParameterSpec bundles the tag length and IV together.
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            // 3. Encrypt. doFinal returns ciphertext concatenated with the 16-byte
            //    GCM authentication tag as a single byte array.
            byte[] ciphertextWithTag = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8));

            // 4. Prepend IV: combined = IV || ciphertext+tag
            byte[] combined = new byte[IV_LENGTH_BYTES + ciphertextWithTag.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH_BYTES);
            System.arraycopy(ciphertextWithTag, 0, combined, IV_LENGTH_BYTES, ciphertextWithTag.length);

            // 5. Base64-encode the combined bytes for safe storage in a VARCHAR column.
            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            // Wrap checked JCE exceptions in an unchecked exception so Spring
            // can roll back the JPA transaction cleanly. Never log the plaintext.
            throw new IllegalStateException("PII field encryption failed", e);
        }
    }

    /**
     * Called by Hibernate after every SELECT.
     *
     * <p>Null in → null out: a NULL database value means the optional field
     * was not provided at registration time.
     *
     * @param stored Base64-encoded {@code IV || ciphertext+tag} from the database
     * @return decrypted plaintext String, or null if stored is null
     */
    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            // 1. Decode the Base64 envelope.
            byte[] combined = Base64.getDecoder().decode(stored);

            // 2. Split back into IV and ciphertext+tag.
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] ciphertextWithTag = new byte[combined.length - IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(combined, IV_LENGTH_BYTES, ciphertextWithTag, 0, ciphertextWithTag.length);

            // 3. Initialise the cipher in decryption mode with the same key and IV.
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            // 4. Decrypt. doFinal also verifies the GCM authentication tag.
            //    If the tag does not match (data tampered), it throws AEADBadTagException.
            byte[] plaintext = cipher.doFinal(ciphertextWithTag);

            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            // A decryption failure could mean: wrong key, corrupted ciphertext,
            // or tampered data. All of these are serious and must not be silenced.
            throw new IllegalStateException("PII field decryption failed", e);
        }
    }
}
