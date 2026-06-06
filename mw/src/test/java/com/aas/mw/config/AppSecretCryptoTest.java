package com.aas.mw.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AppSecretCryptoTest {

    @Test
    void encryptDecryptRoundTripSucceeds() {
        String encrypted = AppSecretCrypto.encrypt("super-secret", "master-key-123");

        assertNotEquals("super-secret", encrypted);
        assertEquals("super-secret", AppSecretCrypto.decrypt(encrypted, "master-key-123"));
    }

    @Test
    void wrongMasterKeyFails() {
        String encrypted = AppSecretCrypto.encrypt("super-secret", "master-key-123");

        assertThrows(IllegalArgumentException.class, () -> AppSecretCrypto.decrypt(encrypted, "wrong-key"));
    }

    @Test
    void tamperedCiphertextFails() {
        String encrypted = AppSecretCrypto.encrypt("super-secret", "master-key-123");
        String tampered = encrypted.replaceFirst(".$", encrypted.endsWith("A)") ? "B)" : "A)");

        assertThrows(IllegalArgumentException.class, () -> AppSecretCrypto.decrypt(tampered, "master-key-123"));
    }

    @Test
    void unsupportedVersionFailsClearly() {
        assertThrows(IllegalArgumentException.class,
                () -> AppSecretCrypto.decrypt("ENC(v9:abcd)", "master-key-123"));
    }
}
