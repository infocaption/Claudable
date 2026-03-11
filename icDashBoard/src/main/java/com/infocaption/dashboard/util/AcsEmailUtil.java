package com.infocaption.dashboard.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

/**
 * Utility class for sending emails via Azure Communication Services REST API.
 * Uses HMAC-SHA256 authentication with the ACS access key.
 *
 * Configuration loaded from app_config table (editable in Admin panel).
 * Keys: acs.endpoint, acs.host, acs.accessKey, acs.senderAddress, acs.apiVersion,
 *       email.sendTimeout, email.statusTimeout
 *
 * No external dependencies — uses Java 21 built-in java.net.http.HttpClient.
 */
public class AcsEmailUtil {

    private static final String SEND_PATH = "/emails:send";
    private static final String OPERATIONS_PATH = "/emails/operations/";

    // RFC 1123 date format required by Azure
    private static final DateTimeFormatter RFC1123_FORMATTER = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .withZone(ZoneOffset.UTC);

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    // Helper methods: AppConfig (DB) → SecretsConfig (properties file) → empty
    private static String endpoint() { return AppConfig.get("acs.endpoint", SecretsConfig.get("acs.endpoint", "")); }
    private static String host() { return AppConfig.get("acs.host", SecretsConfig.get("acs.host", "")); }
    private static String accessKey() { return AppConfig.get("acs.accessKey", SecretsConfig.get("acs.accessKey", "")); }
    private static String senderAddress() { return AppConfig.get("acs.senderAddress", SecretsConfig.get("acs.senderAddress", "messenger@infocaption.com")); }
    private static String apiVersion() { return AppConfig.get("acs.apiVersion", SecretsConfig.get("acs.apiVersion", "2023-03-31")); }
    private static int sendTimeout() { return AppConfig.getInt("email.sendTimeout", 30); }
    private static int statusTimeout() { return AppConfig.getInt("email.statusTimeout", 15); }

    /**
     * Send an email to a single recipient via Azure Communication Services.
     *
     * @param toEmail   Recipient email address
     * @param subject   Email subject
     * @param htmlBody  Email body in HTML format
     * @return The operation ID for tracking delivery status, or null on failure
     * @throws Exception if the request fails
     */
    public static String sendEmail(String toEmail, String subject, String htmlBody) throws Exception {
        // Validate that ACS credentials are configured
        if (endpoint().isEmpty() || host().isEmpty() || accessKey().isEmpty()) {
            throw new IllegalStateException(
                "Azure Communication Services not configured. "
                + "Set acs.endpoint, acs.host, and acs.accessKey in Admin → Inställningar.");
        }

        // Build JSON request body
        String jsonBody = buildEmailJson(toEmail, subject, htmlBody);

        // Build path + query
        String pathAndQuery = SEND_PATH + "?api-version=" + apiVersion();
        String fullUrl = endpoint() + pathAndQuery;

        // Generate authentication headers
        String dateHeader = RFC1123_FORMATTER.format(ZonedDateTime.now(ZoneOffset.UTC));
        String contentHash = computeSha256Base64(jsonBody);
        String authorization = computeHmacAuthorization("POST", pathAndQuery, dateHeader, contentHash);

        // Build and send HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header("Content-Type", "application/json")
                .header("x-ms-date", dateHeader)
                .header("x-ms-content-sha256", contentHash)
                .header("Authorization", authorization)
                .header("repeatability-request-id", java.util.UUID.randomUUID().toString())
                .header("repeatability-first-sent", dateHeader)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(sendTimeout()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();
        String responseBody = response.body();

        if (statusCode == 202) {
            // Accepted — extract operation ID from response header or body
            String operationId = response.headers().firstValue("operation-id").orElse(null);
            if (operationId == null) {
                // Try to extract from response body
                operationId = extractJsonValue(responseBody, "id");
            }
            return operationId;
        } else {
            // Error — extract error message
            String errorMessage = extractJsonValue(responseBody, "message");
            if (errorMessage == null) {
                errorMessage = "HTTP " + statusCode + ": " + responseBody;
            }
            throw new Exception("Azure ACS email failed: " + errorMessage);
        }
    }

    /**
     * Check the delivery status of a sent email.
     *
     * @param operationId The operation ID returned from sendEmail()
     * @return Status string: "NotStarted", "Running", "Succeeded", "Failed", "Canceled"
     * @throws Exception if the request fails
     */
    public static String getOperationStatus(String operationId) throws Exception {
        String pathAndQuery = OPERATIONS_PATH + operationId + "?api-version=" + apiVersion();
        String fullUrl = endpoint() + pathAndQuery;

        String dateHeader = RFC1123_FORMATTER.format(ZonedDateTime.now(ZoneOffset.UTC));
        String contentHash = computeSha256Base64(""); // Empty body for GET
        String authorization = computeHmacAuthorization("GET", pathAndQuery, dateHeader, contentHash);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header("x-ms-date", dateHeader)
                .header("x-ms-content-sha256", contentHash)
                .header("Authorization", authorization)
                .GET()
                .timeout(Duration.ofSeconds(statusTimeout()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            String status = extractJsonValue(response.body(), "status");
            return status != null ? status : "Unknown";
        } else {
            throw new Exception("Failed to get operation status: HTTP " + response.statusCode());
        }
    }

    // ==================== HMAC-SHA256 Authentication ====================

    /**
     * Compute the HMAC-SHA256 Authorization header value.
     *
     * StringToSign format:
     *   {HTTP method}\n{pathAndQuery}\n{date};{host};{contentHash}
     */
    private static String computeHmacAuthorization(String method, String pathAndQuery,
                                                     String dateHeader, String contentHash) throws Exception {
        String stringToSign = method + "\n" + pathAndQuery + "\n" + dateHeader + ";" + host() + ";" + contentHash;

        byte[] decodedKey = Base64.getDecoder().decode(accessKey());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(decodedKey, "HmacSHA256"));
        byte[] signatureBytes = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signatureBytes);

        return "HMAC-SHA256 SignedHeaders=x-ms-date;host;x-ms-content-sha256&Signature=" + signature;
    }

    /**
     * Compute SHA-256 hash of content and return as Base64 string.
     */
    private static String computeSha256Base64(String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    // ==================== JSON Helpers ====================

    /**
     * Build the JSON request body for the ACS email send API.
     */
    private static String buildEmailJson(String toEmail, String subject, String htmlBody) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"senderAddress\":").append(JsonUtil.quote(senderAddress())).append(",");
        sb.append("\"recipients\":{\"to\":[{\"address\":").append(JsonUtil.quote(toEmail)).append("}]},");
        sb.append("\"content\":{");
        sb.append("\"subject\":").append(JsonUtil.quote(subject)).append(",");
        sb.append("\"html\":").append(JsonUtil.quote(htmlBody));
        sb.append("}");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Simple JSON value extractor for a top-level string field.
     * Returns null if key not found.
     */
    static String extractJsonValue(String json, String key) {
        if (json == null || key == null) return null;
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
