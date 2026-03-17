package com.infocaption.dashboard.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.sql.*;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

/**
 * Web Push (RFC 8291) implementation using Java 21 built-in crypto.
 * No external JARs — uses ECDH P-256, AES-128-GCM, HKDF-SHA256, ES256.
 */
public class WebPushUtil {

    private static final Logger log = LoggerFactory.getLogger(WebPushUtil.class);

    private static final String EC_CURVE = "secp256r1";
    private static final int KEY_LENGTH = 65; // uncompressed P-256 public key
    private static final int AUTH_LENGTH = 16;
    private static final int CONTENT_KEY_LENGTH = 16;
    private static final int NONCE_LENGTH = 12;
    private static final int PADDING_LENGTH = 2; // two zero-byte padding

    // ==================== VAPID Key Management ====================

    /**
     * Generate VAPID EC P-256 keypair and store in AppConfig.
     * Called once on first startup if keys are empty.
     */
    public static void generateVapidKeys() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec(EC_CURVE));
            KeyPair kp = kpg.generateKeyPair();

            ECPublicKey pub = (ECPublicKey) kp.getPublic();
            ECPrivateKey priv = (ECPrivateKey) kp.getPrivate();

            // Encode public key as uncompressed point (65 bytes)
            byte[] pubBytes = encodeUncompressedPoint(pub);
            String publicKeyB64 = base64UrlEncode(pubBytes);

            // Encode private key as raw 32-byte scalar
            byte[] privBytes = priv.getS().toByteArray();
            // BigInteger may have leading zero byte for sign
            if (privBytes.length > 32) {
                byte[] trimmed = new byte[32];
                System.arraycopy(privBytes, privBytes.length - 32, trimmed, 0, 32);
                privBytes = trimmed;
            } else if (privBytes.length < 32) {
                byte[] padded = new byte[32];
                System.arraycopy(privBytes, 0, padded, 32 - privBytes.length, privBytes.length);
                privBytes = padded;
            }
            String privateKeyB64 = base64UrlEncode(privBytes);

            AppConfig.set("vapid.publicKey", publicKeyB64);
            AppConfig.set("vapid.privateKey", privateKeyB64);
            log.info("VAPID keypair generated and stored in app_config");

        } catch (Exception e) {
            log.error("Failed to generate VAPID keys: {}", e.getMessage());
        }
    }

    /**
     * Ensure VAPID keys exist, generate if missing.
     */
    public static void ensureVapidKeys() {
        String pubKey = AppConfig.get("vapid.publicKey", "");
        if (pubKey == null || pubKey.isEmpty()) {
            generateVapidKeys();
        }
    }

    // ==================== Web Push Encryption (RFC 8291) ====================

    /**
     * Send a push notification to a single subscription.
     *
     * @param endpoint  Push service URL
     * @param p256dhB64 Subscriber's P-256 public key (Base64url)
     * @param authB64   Subscriber's auth secret (Base64url)
     * @param payload   Plaintext notification JSON
     * @return HTTP status code from push service
     */
    public static int sendPush(String endpoint, String p256dhB64, String authB64, String payload) throws Exception {
        byte[] subscriberPubBytes = base64UrlDecode(p256dhB64);
        byte[] authSecret = base64UrlDecode(authB64);
        byte[] plaintext = payload.getBytes(StandardCharsets.UTF_8);

        // Generate ephemeral ECDH keypair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec(EC_CURVE));
        KeyPair ephemeral = kpg.generateKeyPair();
        ECPublicKey ephemeralPub = (ECPublicKey) ephemeral.getPublic();
        byte[] ephemeralPubBytes = encodeUncompressedPoint(ephemeralPub);

        // Reconstruct subscriber public key
        ECPublicKey subscriberPub = decodeUncompressedPoint(subscriberPubBytes);

        // ECDH shared secret
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(ephemeral.getPrivate());
        ka.doPhase(subscriberPub, true);
        byte[] sharedSecret = ka.generateSecret();

        // HKDF: extract PRK from auth secret + shared secret
        byte[] ikm = sharedSecret;
        byte[] salt = authSecret;

        // info for auth secret extraction
        byte[] authInfo = buildInfo("WebPush: info\0", subscriberPubBytes, ephemeralPubBytes);
        byte[] prk = hkdfExtract(salt, ikm);
        byte[] ikm2 = hkdfExpand(prk, authInfo, 32);

        // Derive content encryption key + nonce
        byte[] cekSalt = new byte[16];
        SecureRandom.getInstanceStrong().nextBytes(cekSalt);

        byte[] prk2 = hkdfExtract(cekSalt, ikm2);

        byte[] cekInfo = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
        byte[] cek = hkdfExpand(prk2, cekInfo, CONTENT_KEY_LENGTH);

        byte[] nonceInfo = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);
        byte[] nonce = hkdfExpand(prk2, nonceInfo, NONCE_LENGTH);

        // Pad plaintext (RFC 8188: 2-byte padding length prefix)
        byte[] padded = new byte[PADDING_LENGTH + plaintext.length];
        padded[0] = 0; // padding length high byte
        padded[1] = 0; // padding length low byte — no padding after content
        System.arraycopy(plaintext, 0, padded, PADDING_LENGTH, plaintext.length);

        // AES-128-GCM encrypt
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(padded);

        // Build aes128gcm payload (RFC 8188 header + ciphertext)
        // Header: salt(16) + rs(4) + idlen(1) + keyid(65)
        int recordSize = padded.length + 16 + 1; // content + tag + delimiter
        ByteBuffer body = ByteBuffer.allocate(16 + 4 + 1 + KEY_LENGTH + ciphertext.length + 1);
        body.put(cekSalt);                                // 16 bytes salt
        body.putInt(recordSize);                           // 4 bytes record size
        body.put((byte) KEY_LENGTH);                       // 1 byte keyid length
        body.put(ephemeralPubBytes);                       // 65 bytes ephemeral public key
        body.put(ciphertext);                              // encrypted content
        body.put((byte) 2);                                // record delimiter
        byte[] requestBody = new byte[body.position()];
        body.flip();
        body.get(requestBody);

        // Build VAPID Authorization header (JWT ES256)
        String vapidAuth = buildVapidAuth(endpoint);

        // HTTP POST to push endpoint
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Content-Type", "application/octet-stream");
        conn.setRequestProperty("Content-Encoding", "aes128gcm");
        conn.setRequestProperty("TTL", "86400");
        conn.setRequestProperty("Authorization", vapidAuth);

        String vapidPubKey = AppConfig.get("vapid.publicKey", "");
        conn.setRequestProperty("Crypto-Key", "p256ecdsa=" + vapidPubKey);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody);
        }

        int status = conn.getResponseCode();
        conn.disconnect();
        return status;
    }

    // ==================== Async Subscriber Notification ====================

    /**
     * Notify all matching push subscribers about a CloudGuard incident.
     * Runs async — does not block the caller.
     */
    public static void notifySubscribersAsync(int incidentId, String entityName, String severity, String message) {
        String enabled = AppConfig.get("notifications.enabled", "true");
        if (!"true".equalsIgnoreCase(enabled)) return;

        CompletableFuture.runAsync(() -> {
            try {
                notifySubscribers(incidentId, entityName, severity, message);
            } catch (Exception e) {
                log.error("Async push notification failed: {}", e.getMessage());
            }
        });
    }

    /**
     * Send push to all subscribers whose severity_filter includes the given severity.
     */
    public static void notifySubscribers(int incidentId, String entityName, String severity, String message) {
        String sql = "SELECT id, user_id, endpoint, p256dh_key, auth_key FROM push_subscriptions " +
                     "WHERE severity_filter LIKE ?";

        // Build notification payload JSON
        String truncatedMsg = message;
        if (truncatedMsg != null && truncatedMsg.length() > 200) {
            truncatedMsg = truncatedMsg.substring(0, 200) + "...";
        }
        StringBuilder payload = new StringBuilder("{");
        payload.append("\"title\":\"CloudGuard: ").append(escapeJson(severity.toUpperCase())).append("\"");
        payload.append(",\"body\":\"").append(escapeJson(entityName));
        if (truncatedMsg != null && !truncatedMsg.isEmpty()) {
            payload.append(" — ").append(escapeJson(truncatedMsg));
        }
        payload.append("\"");
        payload.append(",\"severity\":\"").append(escapeJson(severity)).append("\"");
        payload.append(",\"incidentId\":").append(incidentId);
        payload.append(",\"tag\":\"cloudguard-").append(incidentId).append("\"");
        payload.append("}");
        String payloadStr = payload.toString();

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, "%" + severity + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int subId = rs.getInt("id");
                    int userId = rs.getInt("user_id");
                    String endpoint = rs.getString("endpoint");
                    String p256dh = rs.getString("p256dh_key");
                    String auth = rs.getString("auth_key");

                    try {
                        int status = sendPush(endpoint, p256dh, auth, payloadStr);
                        if (status == 410 || status == 404) {
                            // Subscription expired — remove it
                            removeSubscription(subId);
                            logNotification(userId, incidentId, "expired", "HTTP " + status);
                        } else if (status >= 200 && status < 300) {
                            logNotification(userId, incidentId, "sent", null);
                        } else {
                            logNotification(userId, incidentId, "failed", "HTTP " + status);
                        }
                    } catch (Exception e) {
                        log.warn("Push to subscription {} failed: {}", subId, e.getMessage());
                        logNotification(userId, incidentId, "failed", e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Error querying push subscriptions: {}", e.getMessage());
        }
    }

    // ==================== Helpers ====================

    private static void removeSubscription(int subscriptionId) {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM push_subscriptions WHERE id = ?")) {
            ps.setInt(1, subscriptionId);
            ps.executeUpdate();
            log.info("Removed expired push subscription id={}", subscriptionId);
        } catch (SQLException e) {
            log.warn("Failed to remove subscription {}: {}", subscriptionId, e.getMessage());
        }
    }

    private static void logNotification(int userId, int incidentId, String status, String error) {
        String sql = "INSERT INTO notification_log (user_id, incident_id, channel, status, error_message) VALUES (?, ?, 'web_push', ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, incidentId);
            ps.setString(3, status);
            ps.setString(4, error);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("Failed to log notification: {}", e.getMessage());
        }
    }

    /** Build VAPID Authorization header (vapid t=JWT,k=publicKey). */
    private static String buildVapidAuth(String endpoint) throws Exception {
        String vapidPrivB64 = AppConfig.get("vapid.privateKey", "");
        String vapidPubB64 = AppConfig.get("vapid.publicKey", "");
        String subject = AppConfig.get("vapid.subject", "mailto:admin@infocaption.com");

        // Extract audience (origin) from endpoint URL
        URL url = new URL(endpoint);
        String audience = url.getProtocol() + "://" + url.getHost();

        long now = System.currentTimeMillis() / 1000;
        long exp = now + 86400; // 24 hours

        // JWT header + payload
        String header = base64UrlEncode("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = base64UrlEncode(("{\"aud\":\"" + audience + "\",\"exp\":" + exp + ",\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
        String unsigned = header + "." + claims;

        // Sign with ES256
        byte[] privKeyBytes = base64UrlDecode(vapidPrivB64);
        ECPrivateKey privKey = decodePrivateKey(privKeyBytes);

        Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
        sig.initSign(privKey);
        sig.update(unsigned.getBytes(StandardCharsets.US_ASCII));
        byte[] sigBytes = sig.sign();

        String jwt = unsigned + "." + base64UrlEncode(sigBytes);
        return "vapid t=" + jwt + ",k=" + vapidPubB64;
    }

    /** HKDF-Extract: HMAC-SHA256(salt, ikm). */
    private static byte[] hkdfExtract(byte[] salt, byte[] ikm) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt.length > 0 ? salt : new byte[32], "HmacSHA256"));
        return mac.doFinal(ikm);
    }

    /** HKDF-Expand: derive key material of given length. */
    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));

        byte[] result = new byte[length];
        byte[] t = new byte[0];
        int offset = 0;
        byte counter = 1;

        while (offset < length) {
            mac.reset();
            mac.update(t);
            mac.update(info);
            mac.update(counter);
            t = mac.doFinal();
            int toCopy = Math.min(t.length, length - offset);
            System.arraycopy(t, 0, result, offset, toCopy);
            offset += toCopy;
            counter++;
        }
        return result;
    }

    /** Build info parameter for WebPush HKDF: "WebPush: info\0" + subscriber_pub + sender_pub. */
    private static byte[] buildInfo(String prefix, byte[] subscriberPub, byte[] senderPub) {
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer buf = ByteBuffer.allocate(prefixBytes.length + subscriberPub.length + senderPub.length);
        buf.put(prefixBytes);
        buf.put(subscriberPub);
        buf.put(senderPub);
        return buf.array();
    }

    /** Encode an EC public key as uncompressed point (0x04 + x + y, 65 bytes). */
    static byte[] encodeUncompressedPoint(ECPublicKey key) {
        byte[] x = toFixedLength(key.getW().getAffineX().toByteArray(), 32);
        byte[] y = toFixedLength(key.getW().getAffineY().toByteArray(), 32);
        byte[] result = new byte[KEY_LENGTH];
        result[0] = 0x04;
        System.arraycopy(x, 0, result, 1, 32);
        System.arraycopy(y, 0, result, 33, 32);
        return result;
    }

    /** Decode uncompressed P-256 point bytes to ECPublicKey. */
    static ECPublicKey decodeUncompressedPoint(byte[] bytes) throws Exception {
        if (bytes.length != KEY_LENGTH || bytes[0] != 0x04)
            throw new IllegalArgumentException("Invalid uncompressed EC point");

        byte[] x = new byte[32];
        byte[] y = new byte[32];
        System.arraycopy(bytes, 1, x, 0, 32);
        System.arraycopy(bytes, 33, y, 0, 32);

        ECPoint point = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec(EC_CURVE));
        ECParameterSpec spec = params.getParameterSpec(ECParameterSpec.class);

        ECPublicKeySpec pubSpec = new ECPublicKeySpec(point, spec);
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(pubSpec);
    }

    /** Decode raw 32-byte scalar to ECPrivateKey. */
    static ECPrivateKey decodePrivateKey(byte[] raw) throws Exception {
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec(EC_CURVE));
        ECParameterSpec spec = params.getParameterSpec(ECParameterSpec.class);

        ECPrivateKeySpec privSpec = new ECPrivateKeySpec(new BigInteger(1, raw), spec);
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(privSpec);
    }

    /** Pad or trim a BigInteger byte array to fixed length. */
    private static byte[] toFixedLength(byte[] bytes, int length) {
        if (bytes.length == length) return bytes;
        byte[] result = new byte[length];
        if (bytes.length > length) {
            System.arraycopy(bytes, bytes.length - length, result, 0, length);
        } else {
            System.arraycopy(bytes, 0, result, length - bytes.length, bytes.length);
        }
        return result;
    }

    static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    static byte[] base64UrlDecode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }

    /** Simple JSON string escaper for notification payloads. */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
