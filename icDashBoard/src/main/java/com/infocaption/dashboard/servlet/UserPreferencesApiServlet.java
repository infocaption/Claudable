package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.CryptoUtil;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

/**
 * REST API for per-user preferences (key-value pairs).
 *
 * GET  /api/preferences            — returns all preferences for the logged-in user
 * GET  /api/preferences?key=X      — returns a single preference value
 * POST /api/preferences            — upsert one or more preferences
 *       Body: {"key":"...","value":"..."} or {"preferences":[{"key":"...","value":"..."},…]}
 * DELETE /api/preferences?key=X    — delete a single preference
 */
public class UserPreferencesApiServlet extends HttpServlet {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_.:-]{1,100}$");

    /** Preference keys that contain sensitive credentials and should be encrypted at rest. */
    private static boolean isSensitiveKey(String key) {
        return key != null && (key.endsWith(".apiToken") || key.endsWith(".password")
                || key.endsWith(".secret") || key.endsWith(".accessKey"));
    }

    @Override
    public void init() throws ServletException {
        // Ensure table exists (idempotent)
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS user_preferences (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  user_id INT NOT NULL," +
                "  pref_key VARCHAR(100) NOT NULL," +
                "  pref_value TEXT NULL," +
                "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  UNIQUE KEY uq_user_pref (user_id, pref_key)," +
                "  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            log("user_preferences table check failed: " + e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        User user = (User) req.getSession().getAttribute("user");
        if (user == null) {
            resp.setStatus(401);
            resp.getWriter().write("{\"error\":\"Not authenticated\"}");
            return;
        }

        String key = req.getParameter("key");

        if (key != null) {
            // Single preference
            getSinglePreference(user.getId(), key, resp);
        } else {
            // All preferences
            getAllPreferences(user.getId(), resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        User user = (User) req.getSession().getAttribute("user");
        if (user == null) {
            resp.setStatus(401);
            resp.getWriter().write("{\"error\":\"Not authenticated\"}");
            return;
        }

        String body = readBody(req);

        // Check if it's an array of preferences
        if (body.contains("\"preferences\"")) {
            upsertMultiple(user.getId(), body, resp);
        } else {
            upsertSingle(user.getId(), body, resp);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        User user = (User) req.getSession().getAttribute("user");
        if (user == null) {
            resp.setStatus(401);
            resp.getWriter().write("{\"error\":\"Not authenticated\"}");
            return;
        }

        String key = req.getParameter("key");
        if (key == null || key.isEmpty()) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Missing key parameter\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM user_preferences WHERE user_id = ? AND pref_key = ?")) {
            ps.setInt(1, user.getId());
            ps.setString(2, key);
            int rows = ps.executeUpdate();
            resp.getWriter().write("{\"deleted\":" + rows + "}");
        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void getSinglePreference(int userId, String key, HttpServletResponse resp) throws IOException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT pref_value FROM user_preferences WHERE user_id = ? AND pref_key = ?")) {
            ps.setInt(1, userId);
            ps.setString(2, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String val = rs.getString("pref_value");
                // Decrypt sensitive values
                if (isSensitiveKey(key)) val = CryptoUtil.decrypt(val);
                resp.getWriter().write("{\"key\":" + JsonUtil.quote(key) + ",\"value\":" + JsonUtil.quote(val) + "}");
            } else {
                resp.setStatus(404);
                resp.getWriter().write("{\"error\":\"Not found\"}");
            }
        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void getAllPreferences(int userId, HttpServletResponse resp) throws IOException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT pref_key, pref_value FROM user_preferences WHERE user_id = ? ORDER BY pref_key")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                first = false;
                String prefKey = rs.getString("pref_key");
                String prefValue = rs.getString("pref_value");
                // Decrypt sensitive values
                if (isSensitiveKey(prefKey)) prefValue = CryptoUtil.decrypt(prefValue);
                sb.append(JsonUtil.quote(prefKey));
                sb.append(":");
                sb.append(JsonUtil.quote(prefValue));
            }
            sb.append("}");
            resp.getWriter().write(sb.toString());
        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void upsertSingle(int userId, String body, HttpServletResponse resp) throws IOException {
        String key = JsonUtil.extractJsonString(body, "key");
        String value = JsonUtil.extractJsonString(body, "value");

        if (key == null || key.isEmpty()) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Missing key\"}");
            return;
        }

        if (!KEY_PATTERN.matcher(key).matches()) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Invalid key format\"}");
            return;
        }

        // Encrypt sensitive preference values (e.g. jira.apiToken)
        String storedValue = isSensitiveKey(key) ? CryptoUtil.encrypt(value) : value;

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO user_preferences (user_id, pref_key, pref_value) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE pref_value = VALUES(pref_value)")) {
            ps.setInt(1, userId);
            ps.setString(2, key);
            ps.setString(3, storedValue);
            ps.executeUpdate();
            resp.getWriter().write("{\"saved\":true,\"key\":" + JsonUtil.quote(key) + "}");
        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void upsertMultiple(int userId, String body, HttpServletResponse resp) throws IOException {
        // Extract the "preferences" array manually
        int arrStart = body.indexOf("[");
        int arrEnd = body.lastIndexOf("]");
        if (arrStart < 0 || arrEnd < 0) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Invalid preferences array\"}");
            return;
        }

        String arrContent = body.substring(arrStart + 1, arrEnd);
        // Split by },{ pattern
        String[] items = arrContent.split("\\}\\s*,\\s*\\{");
        int saved = 0;

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO user_preferences (user_id, pref_key, pref_value) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE pref_value = VALUES(pref_value)")) {

            for (String item : items) {
                // Ensure item has braces
                String obj = item.trim();
                if (!obj.startsWith("{")) obj = "{" + obj;
                if (!obj.endsWith("}")) obj = obj + "}";

                String key = JsonUtil.extractJsonString(obj, "key");
                String value = JsonUtil.extractJsonString(obj, "value");

                if (key == null || key.isEmpty() || !KEY_PATTERN.matcher(key).matches()) continue;

                // Encrypt sensitive preference values
                String storedValue = isSensitiveKey(key) ? CryptoUtil.encrypt(value) : value;

                ps.setInt(1, userId);
                ps.setString(2, key);
                ps.setString(3, storedValue);
                ps.addBatch();
                saved++;
            }

            ps.executeBatch();
            resp.getWriter().write("{\"saved\":" + saved + "}");
        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

}
