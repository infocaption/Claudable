package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.AppConfig;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.JsonUtil;
import com.infocaption.dashboard.util.WebPushUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.*;

/**
 * Push Notification API — subscribe/unsubscribe browsers, manage preferences, send test.
 *
 * GET  /api/notifications/vapid-key      — Return VAPID public key (for subscription)
 * POST /api/notifications/subscribe      — Save browser push subscription
 * POST /api/notifications/unsubscribe    — Remove subscription by endpoint
 * GET  /api/notifications/subscriptions  — List user's active subscriptions
 * PUT  /api/notifications/preferences    — Update severity filter
 * POST /api/notifications/test           — Send a test push notification
 *
 * All endpoints require authentication (AuthFilter).
 */
public class NotificationServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(NotificationServlet.class);

    // ==================== Init ====================

    @Override
    public void init() throws ServletException {
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS push_subscriptions (" +
                "  id              INT AUTO_INCREMENT PRIMARY KEY," +
                "  user_id         INT NOT NULL," +
                "  endpoint        VARCHAR(2000) NOT NULL," +
                "  p256dh_key      VARCHAR(500) NOT NULL," +
                "  auth_key        VARCHAR(500) NOT NULL," +
                "  severity_filter VARCHAR(100) NOT NULL DEFAULT 'critical,error,warning,info'," +
                "  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  INDEX idx_pushsub_user (user_id)," +
                "  UNIQUE INDEX idx_pushsub_endpoint (endpoint(500))" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS notification_log (" +
                "  id              BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  user_id         INT NULL," +
                "  incident_id     INT NULL," +
                "  channel         VARCHAR(20) NOT NULL DEFAULT 'web_push'," +
                "  status          ENUM('sent','failed','expired') NOT NULL," +
                "  error_message   TEXT NULL," +
                "  sent_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  INDEX idx_notiflog_sent (sent_at)," +
                "  INDEX idx_notiflog_user (user_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            log.info("Push notification tables verified/created");
        } catch (SQLException e) {
            log.warn("Could not auto-create push notification tables: {}", e.getMessage());
        }

        // Generate VAPID keys if they don't exist yet
        WebPushUtil.ensureVapidKeys();
    }

    // ==================== GET ====================

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        if (pathInfo.equals("/vapid-key") || pathInfo.equals("/vapid-key/")) {
            handleGetVapidKey(resp);
        } else if (pathInfo.equals("/subscriptions") || pathInfo.equals("/subscriptions/")) {
            User user = getUser(req);
            if (user == null) { sendUnauth(resp); return; }
            handleListSubscriptions(user.getId(), resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    // ==================== POST ====================

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        User user = getUser(req);
        if (user == null) { sendUnauth(resp); return; }

        String body = readRequestBody(req);

        if (pathInfo.equals("/subscribe") || pathInfo.equals("/subscribe/")) {
            handleSubscribe(user.getId(), body, resp);
        } else if (pathInfo.equals("/unsubscribe") || pathInfo.equals("/unsubscribe/")) {
            handleUnsubscribe(user.getId(), body, resp);
        } else if (pathInfo.equals("/test") || pathInfo.equals("/test/")) {
            handleTest(user.getId(), resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    // ==================== PUT ====================

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        User user = getUser(req);
        if (user == null) { sendUnauth(resp); return; }

        if (pathInfo.equals("/preferences") || pathInfo.equals("/preferences/")) {
            String body = readRequestBody(req);
            handleUpdatePreferences(user.getId(), body, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    // ==================== Handlers ====================

    private void handleGetVapidKey(HttpServletResponse resp) throws IOException {
        String publicKey = AppConfig.get("vapid.publicKey", "");
        String enabled = AppConfig.get("notifications.enabled", "true");
        StringBuilder json = new StringBuilder("{");
        json.append("\"publicKey\":").append(JsonUtil.quote(publicKey));
        json.append(",\"enabled\":").append("true".equalsIgnoreCase(enabled));
        json.append("}");
        resp.getWriter().write(json.toString());
    }

    private void handleSubscribe(int userId, String body, HttpServletResponse resp) throws IOException {
        String endpoint = JsonUtil.extractJsonString(body, "endpoint");
        String p256dh = JsonUtil.extractJsonString(body, "p256dh");
        String auth = JsonUtil.extractJsonString(body, "auth");

        if (endpoint == null || endpoint.isEmpty() || p256dh == null || p256dh.isEmpty()
                || auth == null || auth.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"endpoint, p256dh and auth are required\"}");
            return;
        }

        // Validate endpoint URL
        if (!endpoint.startsWith("https://")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"endpoint must be HTTPS\"}");
            return;
        }
        if (endpoint.length() > 2000) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"endpoint too long\"}");
            return;
        }

        String sql = "INSERT INTO push_subscriptions (user_id, endpoint, p256dh_key, auth_key) " +
                     "VALUES (?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), p256dh_key = VALUES(p256dh_key), " +
                     "auth_key = VALUES(auth_key), created_at = CURRENT_TIMESTAMP";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, endpoint);
            ps.setString(3, p256dh);
            ps.setString(4, auth);
            ps.executeUpdate();

            log.info("Push subscription saved for user {}", userId);
            resp.getWriter().write("{\"success\":true}");

        } catch (SQLException e) {
            log.error("Error saving push subscription: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleUnsubscribe(int userId, String body, HttpServletResponse resp) throws IOException {
        String endpoint = JsonUtil.extractJsonString(body, "endpoint");
        if (endpoint == null || endpoint.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"endpoint is required\"}");
            return;
        }

        String sql = "DELETE FROM push_subscriptions WHERE user_id = ? AND endpoint = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, endpoint);
            int rows = ps.executeUpdate();

            if (rows > 0) {
                log.info("Push subscription removed for user {}", userId);
                resp.getWriter().write("{\"success\":true}");
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"Subscription not found\"}");
            }
        } catch (SQLException e) {
            log.error("Error removing push subscription: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleListSubscriptions(int userId, HttpServletResponse resp) throws IOException {
        String sql = "SELECT id, endpoint, severity_filter, created_at FROM push_subscriptions WHERE user_id = ? ORDER BY created_at DESC";
        StringBuilder json = new StringBuilder("[");

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{");
                    json.append("\"id\":").append(rs.getInt("id"));
                    json.append(",\"endpoint\":").append(JsonUtil.quote(rs.getString("endpoint")));
                    json.append(",\"severityFilter\":").append(JsonUtil.quote(rs.getString("severity_filter")));
                    json.append(",\"createdAt\":").append(JsonUtil.quote(rs.getString("created_at")));
                    json.append("}");
                }
            }
        } catch (SQLException e) {
            log.error("Error listing subscriptions: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
            return;
        }

        json.append("]");
        resp.getWriter().write(json.toString());
    }

    private void handleUpdatePreferences(int userId, String body, HttpServletResponse resp) throws IOException {
        String severityFilter = JsonUtil.extractJsonString(body, "severityFilter");
        if (severityFilter == null || severityFilter.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"severityFilter is required\"}");
            return;
        }

        // Validate that only allowed severities are in the filter
        String[] parts = severityFilter.split(",");
        StringBuilder cleaned = new StringBuilder();
        for (String part : parts) {
            String s = part.trim().toLowerCase();
            if (s.equals("critical") || s.equals("error") || s.equals("warning") || s.equals("info")) {
                if (cleaned.length() > 0) cleaned.append(",");
                cleaned.append(s);
            }
        }
        if (cleaned.length() == 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"At least one valid severity level is required\"}");
            return;
        }

        String sql = "UPDATE push_subscriptions SET severity_filter = ? WHERE user_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cleaned.toString());
            ps.setInt(2, userId);
            int rows = ps.executeUpdate();

            resp.getWriter().write("{\"success\":true,\"updated\":" + rows + ",\"severityFilter\":" +
                    JsonUtil.quote(cleaned.toString()) + "}");
        } catch (SQLException e) {
            log.error("Error updating preferences: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleTest(int userId, HttpServletResponse resp) throws IOException {
        String sql = "SELECT endpoint, p256dh_key, auth_key FROM push_subscriptions WHERE user_id = ? LIMIT 1";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    resp.getWriter().write("{\"error\":\"No active subscription found. Enable notifications first.\"}");
                    return;
                }

                String endpoint = rs.getString("endpoint");
                String p256dh = rs.getString("p256dh_key");
                String auth = rs.getString("auth_key");

                String payload = "{\"title\":\"Testnotis\",\"body\":\"Push-notifikationer fungerar!\",\"severity\":\"info\",\"tag\":\"test\"}";
                int status = WebPushUtil.sendPush(endpoint, p256dh, auth, payload);

                if (status >= 200 && status < 300) {
                    resp.getWriter().write("{\"success\":true,\"status\":" + status + "}");
                } else {
                    resp.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
                    resp.getWriter().write("{\"error\":\"Push service returned HTTP " + status + "\",\"status\":" + status + "}");
                }
            }
        } catch (SQLException e) {
            log.error("Error sending test notification: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        } catch (Exception e) {
            log.error("Error sending test push: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Push failed: " + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    // ==================== Helpers ====================

    private User getUser(HttpServletRequest req) {
        // Check token-based auth first
        User tokenUser = (User) req.getAttribute("apiTokenUser");
        if (tokenUser != null) return tokenUser;
        // Session auth
        HttpSession session = req.getSession(false);
        if (session != null) return (User) session.getAttribute("user");
        return null;
    }

    private void sendUnauth(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.getWriter().write("{\"error\":\"Not authenticated\"}");
    }

    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
