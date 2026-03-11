package com.infocaption.dashboard.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AES-256-GCM encryption/decryption utility for protecting stored credentials.
 *
 * Key source: AppConfig.get("crypto.masterKey", "") — auto-generated on first use.
 * Encrypted values are prefixed with "ENC:" so plaintext vs encrypted can be detected.
 * Output format: Base64(IV + ciphertext + GCM tag).
 *
 * If decryption fails (e.g. master key changed), the original value is returned
 * with a log warning — graceful degradation.
 */
public class CryptoUtil {
    private static final Logger log = LoggerFactory.getLogger(CryptoUtil.class);
    private static final String PREFIX = "ENC:";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Derive a 256-bit AES key from the master key string via SHA-256.
     * If no master key is configured, auto-generates one and stores it.
     */
    private static byte[] getKey() {
        String masterKey = AppConfig.get("crypto.masterKey", "");
        if (masterKey.isEmpty()) {
            // Auto-generate a master key on first use
            byte[] keyBytes = new byte[32];
            RANDOM.nextBytes(keyBytes);
            masterKey = Base64.getEncoder().encodeToString(keyBytes);
            AppConfig.set("crypto.masterKey", masterKey);
            log.info("Auto-generated crypto master key");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(masterKey.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Check whether a value is already encrypted (has the ENC: prefix).
     */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * Encrypt a plaintext string using AES-256-GCM.
     * Returns "ENC:" + Base64(IV + ciphertext + GCM tag).
     * Returns the original value if null, empty, or already encrypted.
     * On encryption failure, returns plaintext as fallback.
     */
    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        if (isEncrypted(plaintext)) return plaintext; // already encrypted
        try {
            byte[] key = getKey();
            byte[] iv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // IV + ciphertext (includes GCM tag appended by Java)
            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

            return PREFIX + Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            return plaintext; // Fallback: return plaintext
        }
    }

    /**
     * Decrypt a value previously encrypted by {@link #encrypt(String)}.
     * If the value is not encrypted (no ENC: prefix), returns it as-is.
     * If decryption fails (key changed, corrupted data), returns the original
     * encrypted string with a log warning.
     */
    public static String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) return encrypted;
        if (!isEncrypted(encrypted)) return encrypted; // not encrypted, return as-is
        try {
            byte[] key = getKey();
            byte[] data = Base64.getDecoder().decode(encrypted.substring(PREFIX.length()));

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(data, 0, iv, 0, iv.length);
            byte[] ciphertext = new byte[data.length - iv.length];
            System.arraycopy(data, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Decryption failed (key may have changed): {}", e.getMessage());
            return encrypted; // Return original if can't decrypt
        }
    }
}
