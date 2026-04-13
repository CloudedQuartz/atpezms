package com.atpezms.atpezms.common.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link StringEncryptionConverter}.
 *
 * <h2>Why no Spring context here?</h2>
 * {@code StringEncryptionConverter} takes its key as a constructor parameter.
 * We can instantiate it directly in a test with a known key and exercise all
 * code paths without booting Spring or JPA. This is the fastest and most
 * focused kind of test -- a true unit test with zero framework overhead.
 *
 * <h2>The test key</h2>
 * We derive the test key inline by Base64-encoding a known ASCII string that
 * is exactly 32 bytes long. This makes the key value human-readable in the
 * source and the intent obvious: it is <em>only for testing</em>. Never use
 * any variant of this key in a non-test environment.
 */
class StringEncryptionConverterTest {

    /**
     * A deterministic 32-byte test key derived from a fixed ASCII string.
     * "AtpezmsTestKeyForUnitTests000000" is exactly 32 ASCII characters
     * (= 32 bytes), so Base64-encoding it yields a valid 256-bit AES key.
     *
     * <strong>Never use this key outside of tests.</strong>
     */
    private static final String TEST_KEY_BASE64 = Base64.getEncoder()
            .encodeToString("AtpezmsTestKeyForUnitTests000000"
                    .getBytes(StandardCharsets.UTF_8));

    /** The converter under test, constructed with the test key. */
    private final StringEncryptionConverter converter =
            new StringEncryptionConverter(TEST_KEY_BASE64);

    // -----------------------------------------------------------------------
    // Core round-trip correctness
    // -----------------------------------------------------------------------

    @Test
    void shouldRoundTripPlaintext() {
        // The most fundamental invariant: decrypt(encrypt(x)) == x.
        // If this fails, no PII could ever be read back correctly.
        String plaintext = "Jane Doe";

        String ciphertext = converter.convertToDatabaseColumn(plaintext);
        String recovered  = converter.convertToEntityAttribute(ciphertext);

        assertThat(recovered).isEqualTo(plaintext);
    }

    @Test
    void shouldRoundTripUnicodeCharacters() {
        // Visitor names can contain non-ASCII characters (e.g. diacritics,
        // East Asian scripts). The converter must handle UTF-8 correctly.
        String plaintext = "Añā Müller";

        String ciphertext = converter.convertToDatabaseColumn(plaintext);
        String recovered  = converter.convertToEntityAttribute(ciphertext);

        assertThat(recovered).isEqualTo(plaintext);
    }

    @Test
    void shouldRoundTripEmptyString() {
        // An empty string is a valid (if unusual) input. It must not cause
        // an exception or return null.
        String plaintext = "";

        String ciphertext = converter.convertToDatabaseColumn(plaintext);
        String recovered  = converter.convertToEntityAttribute(ciphertext);

        assertThat(recovered).isEqualTo(plaintext);
    }

    // -----------------------------------------------------------------------
    // Randomised IV: same plaintext produces different ciphertexts
    // -----------------------------------------------------------------------

    @Test
    void shouldProduceDifferentCiphertextForSamePlaintext() {
        // Each call generates a fresh random 12-byte IV. Two encryptions of
        // the same plaintext must therefore produce different ciphertexts.
        // If they were the same, an attacker could use a lookup table (known
        // as a "rainbow table") to reverse-engineer the plaintext from the
        // database column values.
        String plaintext = "same value";

        String ciphertext1 = converter.convertToDatabaseColumn(plaintext);
        String ciphertext2 = converter.convertToDatabaseColumn(plaintext);

        assertThat(ciphertext1).isNotEqualTo(ciphertext2);
    }

    // -----------------------------------------------------------------------
    // Null handling (nullable PII columns: email, phone)
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnNullWhenEncryptingNull() {
        // Nullable columns (email_enc, phone_enc) must store NULL in the
        // database, not the ciphertext of an empty string.
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void shouldReturnNullWhenDecryptingNull() {
        // Reading a NULL column must return null to the entity field, not
        // throw an exception or return an empty string.
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    // -----------------------------------------------------------------------
    // Fail-fast: wrong key length
    // -----------------------------------------------------------------------

    @Test
    void shouldRejectKeyThatIsNotThirtyTwoBytes() {
        // AES-256 requires a 32-byte key. A shorter key must be rejected at
        // construction time so the misconfiguration is caught at startup, not
        // silently at the first encryption attempt.
        String shortKeyBase64 = Base64.getEncoder()
                .encodeToString("tooshort".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new StringEncryptionConverter(shortKeyBase64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    // -----------------------------------------------------------------------
    // Tamper detection (GCM authentication tag)
    // -----------------------------------------------------------------------

    @Test
    void shouldThrowWhenStoredValueIsTampered() {
        // AES-GCM produces an authentication tag alongside the ciphertext.
        // If even a single byte of the stored value is altered (e.g. a
        // database administrator modifying a column directly), decryption
        // must fail loudly. Silently returning garbled plaintext would be
        // worse than an exception: the application would operate on corrupted
        // data without realising it.
        String ciphertext = converter.convertToDatabaseColumn("sensitive");

        // Decode, flip the last byte, re-encode. The last byte is part of
        // the GCM authentication tag, so flipping it invalidates the tag.
        byte[] raw = Base64.getDecoder().decode(ciphertext);
        raw[raw.length - 1] ^= 0xFF;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decryption failed");
    }
}
