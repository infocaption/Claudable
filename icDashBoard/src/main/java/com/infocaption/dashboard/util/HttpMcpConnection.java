package com.infocaption.dashboard.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * MCP connection over Streamable HTTP transport (MCP spec 2024-11-05).
 *
 * Supports:
 *   - JSON and SSE (text/event-stream) response formats
 *   - Mcp-Session-Id header tracking
 *   - Proper initialize handshake
 *   - OAuth 2.0 authorization_code + PKCE flow (token refresh)
 *   - Bearer, API key, Basic auth
 */
public class HttpMcpConnection implements McpConnection {

    private static final Logger log = LoggerFactory.getLogger(HttpMcpConnection.class);

    private final String endpointUrl;
    private final String authType;
    private final String authConfig;
    private final HttpClient httpClient;
    private final int timeoutSeconds;
    private volatile boolean closed = false;
    private volatile String sessionId = null;
    private volatile boolean initialized = false;

    // OAuth token state (for auth_type=oauth with stored refresh_token)
    private volatile String oauthAccessToken = null;
    private volatile long oauthTokenExpiresAt = 0;
    private volatile int serverId = -1; // Set externally for DB updates

    public HttpMcpConnection(String endpointUrl, String authType, String authConfig, int timeoutSeconds) {
        this.endpointUrl = endpointUrl;
        this.authType = authType != null ? authType : "none";
        this.authConfig = authConfig;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void setServerId(int serverId) {
        this.serverId = serverId;
    }

    @Override
    public String sendRequest(String jsonRpcRequest) throws IOException {
        if (closed) throw new IOException("Connection is closed");

        // SSRF protection
        SyncExecutor.validateUrlNotInternal(endpointUrl);

        // For OAuth: ensure we have a valid access token
        if ("oauth".equals(authType)) {
            ensureOAuthToken();
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRpcRequest, StandardCharsets.UTF_8));

        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }

        applyAuth(builder);

        try {
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            response.headers().firstValue("Mcp-Session-Id").ifPresent(id -> {
                this.sessionId = id;
            });

            // If 401 with OAuth, try refresh and retry once
            if (response.statusCode() == 401 && "oauth".equals(authType)) {
                log.debug("Got 401, refreshing OAuth token...");
                oauthAccessToken = null;
                oauthTokenExpiresAt = 0;
                ensureOAuthToken();

                HttpRequest.Builder retryBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(endpointUrl))
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonRpcRequest, StandardCharsets.UTF_8));
                if (sessionId != null) retryBuilder.header("Mcp-Session-Id", sessionId);
                applyAuth(retryBuilder);

                response = httpClient.send(retryBuilder.build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                response.headers().firstValue("Mcp-Session-Id").ifPresent(id -> {
                    this.sessionId = id;
                });
            }

            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode() + " from MCP server: " +
                        truncate(response.body(), 500));
            }

            int maxSize = AppConfig.getInt("mcp.maxResponseSize", 5242880);
            String body = response.body();
            if (body != null && body.length() > maxSize) {
                throw new IOException("Response exceeds max size (" + maxSize + " bytes)");
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (contentType.contains("text/event-stream")) {
                return extractJsonFromSse(body);
            }

            return body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    public void ensureInitialized() throws IOException {
        if (initialized) return;

        String initReq = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{" +
                "\"protocolVersion\":\"2024-11-05\"," +
                "\"capabilities\":{}," +
                "\"clientInfo\":{\"name\":\"icDashBoard MCP Gateway\",\"version\":\"1.0\"}" +
                "}}";

        String response = sendRequest(initReq);
        log.debug("MCP initialize response from {}: {}", endpointUrl, truncate(response, 200));

        try {
            sendRequest("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        } catch (IOException e) {
            log.debug("Initialized notification: {}", e.getMessage());
        }

        initialized = true;
    }

    @Override
    public String listTools() throws IOException {
        ensureInitialized();
        return sendRequest("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
    }

    @Override
    public boolean isAlive() {
        if (closed) return false;
        try {
            ensureInitialized();
            return true;
        } catch (Exception e) {
            log.debug("Health check failed for {}: {}", endpointUrl, e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        closed = true;
        sessionId = null;
        initialized = false;
        oauthAccessToken = null;
    }

    @Override
    public String getTransportType() {
        return "http";
    }

    // ==================== OAuth 2.0 (authorization_code + PKCE) ====================

    /**
     * Ensure we have a valid OAuth access token.
     * Uses the stored refresh_token to get a new access_token.
     * If no refresh_token is stored, throws an error directing admin to authorize first.
     */
    private void ensureOAuthToken() throws IOException {
        if (oauthAccessToken != null && System.currentTimeMillis() < oauthTokenExpiresAt) {
            return;
        }

        String refreshToken = authConfig != null ? JsonUtil.extractJsonString(authConfig, "refreshToken") : null;
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new IOException("OAuth ej auktoriserad. Klicka 'Auktorisera' i admin-panelen för att logga in.");
        }

        String clientId = authConfig != null ? JsonUtil.extractJsonString(authConfig, "clientId") : null;
        if (clientId == null || clientId.isEmpty()) clientId = "icDashBoard";

        // Discover token endpoint
        String tokenEndpoint = discoverTokenEndpoint();

        // Use refresh_token to get new access_token
        String formBody = "grant_type=refresh_token" +
                "&refresh_token=" + urlEncode(refreshToken) +
                "&client_id=" + urlEncode(clientId);

        log.debug("Refreshing OAuth token from {}", tokenEndpoint);

        HttpRequest tokenReq = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> tokenResp = httpClient.send(tokenReq,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (tokenResp.statusCode() >= 400) {
                throw new IOException("OAuth token refresh failed (HTTP " + tokenResp.statusCode() + "): " +
                        truncate(tokenResp.body(), 300) +
                        ". Klicka 'Auktorisera' i admin-panelen för att logga in igen.");
            }

            String body = tokenResp.body();
            String accessToken = JsonUtil.extractJsonString(body, "access_token");
            if (accessToken == null || accessToken.isEmpty()) {
                throw new IOException("OAuth response saknar access_token: " + truncate(body, 300));
            }

            int expiresIn = JsonUtil.extractJsonInt(body, "expires_in");
            if (expiresIn <= 0) expiresIn = 3600;

            this.oauthAccessToken = accessToken;
            this.oauthTokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L) - 30000;

            // If response includes a new refresh_token, persist it
            String newRefreshToken = JsonUtil.extractJsonString(body, "refresh_token");
            if (newRefreshToken != null && !newRefreshToken.isEmpty() && serverId > 0) {
                updateStoredRefreshToken(serverId, clientId, newRefreshToken);
            }

            log.info("OAuth token refreshed for {}, expires in {}s", endpointUrl, expiresIn);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("OAuth token refresh interrupted", e);
        }
    }

    /**
     * Discover the OAuth token endpoint from well-known metadata.
     */
    private String discoverTokenEndpoint() throws IOException {
        URI mcpUri = URI.create(endpointUrl);
        String origin = mcpUri.getScheme() + "://" + mcpUri.getAuthority();

        String[] urls = {
            origin + "/.well-known/oauth-authorization-server"
        };

        for (String url : urls) {
            try {
                SyncExecutor.validateUrlNotInternal(url);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> resp = httpClient.send(req,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (resp.statusCode() == 200) {
                    String tokenEndpoint = JsonUtil.extractJsonString(resp.body(), "token_endpoint");
                    if (tokenEndpoint != null && !tokenEndpoint.isEmpty()) {
                        log.debug("Discovered OAuth token endpoint: {}", tokenEndpoint);
                        return tokenEndpoint;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("OAuth discovery interrupted", e);
            } catch (Exception e) {
                log.debug("OAuth discovery at {} failed: {}", url, e.getMessage());
            }
        }

        throw new IOException("Kunde inte hitta OAuth token endpoint för " + origin);
    }

    /**
     * Update the stored refresh_token in mcp_servers.auth_config.
     */
    private static void updateStoredRefreshToken(int serverId, String clientId, String newRefreshToken) {
        String newConfig = "{\"clientId\":\"" + clientId + "\",\"refreshToken\":\"" + newRefreshToken + "\"}";
        try (java.sql.Connection conn = DBUtil.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                "UPDATE mcp_servers SET auth_config = ? WHERE id = ?")) {
            ps.setString(1, newConfig);
            ps.setInt(2, serverId);
            ps.executeUpdate();
        } catch (java.sql.SQLException e) {
            log.warn("Failed to update refresh_token for server {}: {}", serverId, e.getMessage());
        }
    }

    // ==================== Static OAuth helpers (used by McpAdminServlet) ====================

    /**
     * Build the OAuth authorization URL for the browser redirect.
     * Returns {url, codeVerifier} — codeVerifier must be stored in session.
     */
    public static String[] buildAuthorizationUrl(String mcpEndpointUrl, String clientId, String redirectUri, String state) throws IOException {
        // Discover authorization endpoint
        URI mcpUri = URI.create(mcpEndpointUrl);
        String origin = mcpUri.getScheme() + "://" + mcpUri.getAuthority();
        String discoveryUrl = origin + "/.well-known/oauth-authorization-server";

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(discoveryUrl)).timeout(Duration.ofSeconds(10)).GET().build();

        String authEndpoint;
        try {
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            authEndpoint = JsonUtil.extractJsonString(resp.body(), "authorization_endpoint");
            if (authEndpoint == null) throw new IOException("No authorization_endpoint in discovery");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Discovery interrupted", e);
        }

        // Generate PKCE code verifier + challenge
        SecureRandom random = new SecureRandom();
        byte[] verifierBytes = new byte[32];
        random.nextBytes(verifierBytes);
        String codeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes);

        String codeChallenge;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IOException("SHA-256 not available", e);
        }

        String url = authEndpoint +
                "?response_type=code" +
                "&client_id=" + urlEncode(clientId) +
                "&redirect_uri=" + urlEncode(redirectUri) +
                "&code_challenge=" + urlEncode(codeChallenge) +
                "&code_challenge_method=S256" +
                "&state=" + urlEncode(state) +
                "&scope=" + urlEncode("mcp:read mcp:write");

        return new String[] { url, codeVerifier };
    }

    /**
     * Exchange an authorization code for tokens.
     * Returns the raw token response JSON (contains access_token, refresh_token, etc.)
     */
    public static String exchangeCodeForTokens(String mcpEndpointUrl, String clientId,
                                                String code, String codeVerifier, String redirectUri) throws IOException {
        // Discover token endpoint
        URI mcpUri = URI.create(mcpEndpointUrl);
        String origin = mcpUri.getScheme() + "://" + mcpUri.getAuthority();
        String discoveryUrl = origin + "/.well-known/oauth-authorization-server";

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(discoveryUrl)).timeout(Duration.ofSeconds(10)).GET().build();

        String tokenEndpoint;
        try {
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            tokenEndpoint = JsonUtil.extractJsonString(resp.body(), "token_endpoint");
            if (tokenEndpoint == null) throw new IOException("No token_endpoint in discovery");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Discovery interrupted", e);
        }

        String formBody = "grant_type=authorization_code" +
                "&code=" + urlEncode(code) +
                "&client_id=" + urlEncode(clientId) +
                "&redirect_uri=" + urlEncode(redirectUri) +
                "&code_verifier=" + urlEncode(codeVerifier);

        HttpRequest tokenReq = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> tokenResp = client.send(tokenReq,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (tokenResp.statusCode() >= 400) {
                throw new IOException("Token exchange failed (HTTP " + tokenResp.statusCode() + "): " +
                        tokenResp.body());
            }
            return tokenResp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Token exchange interrupted", e);
        }
    }

    // ==================== SSE Parsing ====================

    private String extractJsonFromSse(String sseBody) {
        if (sseBody == null || sseBody.isEmpty()) return sseBody;

        String lastJson = null;
        StringBuilder dataBuffer = new StringBuilder();

        for (String line : sseBody.split("\n")) {
            if (line.startsWith("data: ")) {
                dataBuffer.append(line.substring(6));
            } else if (line.startsWith("data:")) {
                dataBuffer.append(line.substring(5));
            } else if (line.isEmpty() && dataBuffer.length() > 0) {
                String data = dataBuffer.toString().trim();
                if (data.startsWith("{")) lastJson = data;
                dataBuffer.setLength(0);
            }
        }

        if (dataBuffer.length() > 0) {
            String data = dataBuffer.toString().trim();
            if (data.startsWith("{")) lastJson = data;
        }

        if (lastJson != null) return lastJson;

        String trimmed = sseBody.trim();
        if (trimmed.startsWith("{")) return trimmed;

        log.warn("Could not extract JSON from SSE response: {}", truncate(sseBody, 200));
        return sseBody;
    }

    // ==================== Auth ====================

    private void applyAuth(HttpRequest.Builder builder) {
        if ("oauth".equals(authType)) {
            if (oauthAccessToken != null) {
                builder.header("Authorization", "Bearer " + oauthAccessToken);
            }
            return;
        }

        if ("none".equals(authType) || authConfig == null) return;

        switch (authType) {
            case "bearer": {
                String token = JsonUtil.extractJsonString(authConfig, "token");
                if (token != null) builder.header("Authorization", "Bearer " + token);
                break;
            }
            case "api_key": {
                String header = JsonUtil.extractJsonString(authConfig, "header");
                String value = JsonUtil.extractJsonString(authConfig, "value");
                if (header != null && value != null) builder.header(header, value);
                break;
            }
            case "basic": {
                String username = JsonUtil.extractJsonString(authConfig, "username");
                String password = JsonUtil.extractJsonString(authConfig, "password");
                if (username != null && password != null) {
                    String credentials = Base64.getEncoder().encodeToString(
                            (username + ":" + password).getBytes(StandardCharsets.UTF_8));
                    builder.header("Authorization", "Basic " + credentials);
                }
                break;
            }
        }
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
