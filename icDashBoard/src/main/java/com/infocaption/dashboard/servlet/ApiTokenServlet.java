package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.AppConfig;
import com.infocaption.dashboard.util.AuditUtil;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.JsonUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Base64;

/**
 * API Token Servlet — manage personal API tokens for script/automation access.
 *
 * Requires the user to have has_api_access = 1 (set by admin).
 *
 * GET    /api/tokens           — List user's active tokens (prefix + name + expiry, NOT the full token)
 * POST   /api/tokens           — Generate a new token. Returns the full token ONCE.
 * DELETE /api/tokens?id=N      — Revoke (deactivate) a token
 */
public class ApiTokenServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(ApiTokenServlet.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    // ==================== Init ====================

    @Override
    public void init() throws ServletException {
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS api_tokens (" +
                "  id           INT AUTO_INCREMENT PRIMARY KEY," +
                "  user_id      INT NOT NULL," +
                "  token_hash   VARCHAR(64) NOT NULL," +
                "  token_prefix VARCHAR(8) NOT NULL," +
                "  name         VARCHAR(100) NULL," +
                "  expires_at   TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:01'," +
                "  last_used    TIMESTAMP NULL," +
                "  created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  is_active    TINYINT(1) DEFAULT 1," +
                "  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE," +
                "  UNIQUE INDEX idx_token_hash (token_hash)," +
                "  INDEX idx_token_user (user_id)," +
                "  INDEX idx_token_expires (expires_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            // Ensure has_api_access column exists
            try {
                stmt.executeUpdate("ALTER TABLE users ADD COLUMN has_api_access TINYINT(1) NOT NULL DEFAULT 0 AFTER is_admin");
                log.info("Added has_api_access column to users table");
            } catch (SQLException e) {
                // 1060 = Duplicate column name — already exists, fine
                if (e.getErrorCode() != 1060) {
                    log.warn("Could not add has_api_access column: {}", e.getMessage());
                }
            }

            log.info("API tokens table verified/created successfully");
        } catch (SQLException e) {
            log.warn("Could not auto-create api_tokens table: {}", e.getMessage());
        }
    }

    // ==================== GET — List tokens ====================

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireApiAccess(req, resp);
        if (user == null) return;

        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String sql =
            "SELECT id, token_prefix, name, expires_at, last_used, created_at, " +
            "  CASE WHEN expires_at < NOW() THEN 'expired' " +
            "       WHEN is_active = 0 THEN 'revoked' " +
            "       ELSE 'active' END AS status " +
            "FROM api_tokens " +
            "WHERE user_id = ? " +
            "ORDER BY created_at DESC";

        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, user.getId());
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{");
                    json.append("\"id\":").append(rs.getInt("id"));
                    json.append(",\"tokenPrefix\":").append(JsonUtil.quote(rs.getString("token_prefix")));
                    json.append(",\"name\":").append(JsonUtil.quote(rs.getString("name")));
                    json.append(",\"expiresAt\":").append(JsonUtil.quote(rs.getString("expires_at")));
                    json.append(",\"lastUsed\":").append(JsonUtil.quote(rs.getString("last_used")));
                    json.append(",\"createdAt\":").append(JsonUtil.quote(rs.getString("created_at")));
                    json.append(",\"status\":").append(JsonUtil.quote(rs.getString("status")));
                    json.append("}");
                }
            }
        } catch (SQLException e) {
            log.error("Error listing tokens: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
            return;
        }
        json.append("]");
        resp.getWriter().write(json.toString());
    }

    // ==================== POST — Generate token ====================

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireApiAccess(req, resp);
        if (user == null) return;

        resp.setContentType("application/json; charset=UTF-8");

        // Read optional token name from body
        String body = readBody(req);
        String name = JsonUtil.extractJsonString(body, "name");
        if (name != null && name.length() > 100) name = name.substring(0, 100);

        int maxTokens = AppConfig.getInt("api.maxTokensPerUser", 5);
        int ttlDays = AppConfig.getInt("api.tokenTtlDays", 60);

        try (Connection conn = DBUtil.getConnection()) {

            // Check active token count
            int activeCount = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM api_tokens WHERE user_id = ? AND is_active = 1 AND expires_at > NOW()")) {
                ps.setInt(1, user.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) activeCount = rs.getInt(1);
                }
            }

            if (activeCount >= maxTokens) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Max antal aktiva tokens uppnått (" + maxTokens + "). Återkalla en befintlig token först.\"}");
                return;
            }

            // Generate token: icd_<40 random chars>
            String rawToken = "icd_" + generateRandomString(40);
            String tokenHash = sha256(rawToken);
            String tokenPrefix = rawToken.substring(0, 8); // "icd_XXXX"

            // Insert — use DATE_ADD in SQL to avoid JDBC timezone conversion issues
            String sql =
                "INSERT INTO api_tokens (user_id, token_hash, token_prefix, name, expires_at) " +
                "VALUES (?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL ? DAY))";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, user.getId());
                ps.setString(2, tokenHash);
                ps.setString(3, tokenPrefix);
                ps.setString(4, name);
                ps.setInt(5, ttlDays);
                ps.executeUpdate();

                int tokenId = 0;
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) tokenId = keys.getInt(1);
                }

                // Read back the stored expires_at from DB (avoids timezone mismatch)
                String expiresAtStr = null;
                try (PreparedStatement ps2 = conn.prepareStatement(
                        "SELECT expires_at FROM api_tokens WHERE id = ?")) {
                    ps2.setInt(1, tokenId);
                    try (ResultSet rs2 = ps2.executeQuery()) {
                        if (rs2.next()) expiresAtStr = rs2.getString("expires_at");
                    }
                }

                // Return the full token — this is the ONLY time it's shown
                StringBuilder json = new StringBuilder("{");
                json.append("\"id\":").append(tokenId);
                json.append(",\"token\":").append(JsonUtil.quote(rawToken));
                json.append(",\"tokenPrefix\":").append(JsonUtil.quote(tokenPrefix));
                json.append(",\"name\":").append(JsonUtil.quote(name));
                json.append(",\"expiresAt\":").append(JsonUtil.quote(expiresAtStr));
                json.append(",\"ttlDays\":").append(ttlDays);
                json.append("}");
                resp.getWriter().write(json.toString());

                AuditUtil.logEvent(AuditUtil.TOKEN_CREATE, user.getId(), req, "token", String.valueOf(tokenId),
                        "Created API token: " + tokenPrefix + "... (name=" + name + ")");
            }

        } catch (SQLException e) {
            log.error("Error generating token: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== DELETE — Revoke token ====================

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireApiAccess(req, resp);
        if (user == null) return;

        resp.setContentType("application/json; charset=UTF-8");

        String idStr = req.getParameter("id");
        if (idStr == null || idStr.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing id parameter\"}");
            return;
        }

        int tokenId;
        try { tokenId = Integer.parseInt(idStr); } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid id\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE api_tokens SET is_active = 0 WHERE id = ? AND user_id = ?")) {
            ps.setInt(1, tokenId);
            ps.setInt(2, user.getId());
            int rows = ps.executeUpdate();

            if (rows > 0) {
                AuditUtil.logEvent(AuditUtil.TOKEN_DELETE, user.getId(), req, "token", String.valueOf(tokenId),
                        "Revoked API token id=" + tokenId);

                resp.getWriter().write("{\"success\":true}");
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"Token not found\"}");
            }
        } catch (SQLException e) {
            log.error("Error revoking token: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Static: Validate token (used by AuthFilter) ====================

    /**
     * Validate a raw token string against the database.
     * Returns the User if valid, null otherwise.
     * Also updates last_used timestamp.
     */
    public static User validateToken(String rawToken) {
        if (rawToken == null || !rawToken.startsWith("icd_")) return null;

        String tokenHash = sha256(rawToken);

        String sql =
            "SELECT t.id AS token_id, t.user_id, u.username, u.email, u.full_name, " +
            "       u.is_admin, u.has_api_access, u.is_active " +
            "FROM api_tokens t " +
            "JOIN users u ON t.user_id = u.id " +
            "WHERE t.token_hash = ? AND t.is_active = 1 AND t.expires_at > NOW() " +
            "  AND u.is_active = 1 AND u.has_api_access = 1";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int tokenId = rs.getInt("token_id");

                    User user = new User();
                    user.setId(rs.getInt("user_id"));
                    user.setUsername(rs.getString("username"));
                    user.setEmail(rs.getString("email"));
                    user.setFullName(rs.getString("full_name"));
                    user.setAdmin(rs.getBoolean("is_admin"));
                    user.setActive(true);

                    // Update last_used asynchronously (non-blocking)
                    updateLastUsed(tokenId);

                    return user;
                }
            }
        } catch (SQLException e) {
            LoggerFactory.getLogger(ApiTokenServlet.class).error("Token validation error: {}", e.getMessage());
        }
        return null;
    }

    private static void updateLastUsed(int tokenId) {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE api_tokens SET last_used = NOW() WHERE id = ?")) {
            ps.setInt(1, tokenId);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Non-critical, ignore
        }
    }

    // ==================== Helpers ====================

    /**
     * Require the user to be logged in and have api_access permission.
     */
    private User requireApiAccess(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write("{\"error\":\"Not authenticated\"}");
            return null;
        }

        User user = (User) session.getAttribute("user");

        // Check has_api_access from database (not cached on User object)
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT has_api_access FROM users WHERE id = ?")) {
            ps.setInt(1, user.getId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !rs.getBoolean("has_api_access")) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    resp.setContentType("application/json; charset=UTF-8");
                    resp.getWriter().write("{\"error\":\"Du har inte behörighet för API-åtkomst. Kontakta en admin.\"}");
                    return null;
                }
            }
        } catch (SQLException e) {
            log.error("Error checking api access: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write("{\"error\":\"Database error\"}");
            return null;
        }

        return user;
    }

    private static String generateRandomString(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return encoded.substring(0, Math.min(length, encoded.length()));
    }

    static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

}
