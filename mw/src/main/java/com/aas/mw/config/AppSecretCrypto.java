package com.aas.mw.config;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class AppSecretCrypto {

    public static final String ENV_MASTER_KEY = "APP_CONFIG_MASTER_KEY";
    private static final String PREFIX = "ENC(";
    private static final String SUFFIX = ")";
    private static final String VERSION = "v1";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 65_536;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AppSecretCrypto() {
    }

    public static boolean isEncryptedValue(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.startsWith(PREFIX) && trimmed.endsWith(SUFFIX);
    }

    public static String encrypt(String plaintext, String masterKey) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext is required.");
        }
        validateMasterKey(masterKey);
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] iv = randomBytes(IV_BYTES);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(masterKey, salt), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer payload = ByteBuffer.allocate(salt.length + iv.length + encrypted.length);
            payload.put(salt);
            payload.put(iv);
            payload.put(encrypted);
            return PREFIX + VERSION + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.array()) + SUFFIX;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to encrypt application secret.", ex);
        }
    }

    public static String decrypt(String encryptedValue, String masterKey) {
        validateMasterKey(masterKey);
        if (!isEncryptedValue(encryptedValue)) {
            return encryptedValue;
        }
        String token = encryptedValue.trim().substring(PREFIX.length(), encryptedValue.trim().length() - SUFFIX.length());
        int separator = token.indexOf(':');
        if (separator <= 0) {
            throw new IllegalArgumentException("Invalid encrypted secret format.");
        }
        String version = token.substring(0, separator);
        if (!VERSION.equals(version)) {
            throw new IllegalArgumentException("Unsupported encrypted secret version: " + version);
        }
        byte[] payload = Base64.getUrlDecoder().decode(token.substring(separator + 1));
        if (payload.length <= SALT_BYTES + IV_BYTES) {
            throw new IllegalArgumentException("Encrypted secret payload is incomplete.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        byte[] salt = new byte[SALT_BYTES];
        buffer.get(salt);
        byte[] iv = new byte[IV_BYTES];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(masterKey, salt), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalArgumentException("Unable to decrypt application secret.", ex);
        }
    }

    public static void validateMasterKey(String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalArgumentException("Missing " + ENV_MASTER_KEY + " for encrypted application secrets.");
        }
    }

    private static SecretKey deriveKey(String masterKey, byte[] salt) throws GeneralSecurityException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(masterKey.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }
}
