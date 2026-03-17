package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/**
 * Webhook API Servlet — CRUD for webhook configs + public inbound endpoint.
 *
 * Admin endpoints (require session + admin):
 *   GET    /api/webhook/configs           — List all webhooks
 *   POST   /api/webhook/configs           — Create webhook
 *   PUT    /api/webhook/configs           — Update webhook
 *   DELETE /api/webhook/configs?id=N      — Delete webhook
 *   PUT    /api/webhook/toggle-active     — Toggle is_active
 *   GET    /api/webhook/history?webhookId=N — Run history
 *   GET    /api/webhook/tables            — Allowed target tables
 *   GET    /api/webhook/table-info?table=X — Column metadata
 *
 * Public inbound (no session — own auth):
 *   POST   /api/webhook/inbound/{token}   — Receive JSON data
 */
public class WebhookServlet extends HttpServlet {

    private static final SecureRandom secureRandom = new SecureRandom();

    @Override
    public void init() throws ServletException {
        // Tables created via migration 045
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");

        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/configs")) {
            handleListConfigs(resp);
        } else if (pathInfo.equals("/history")) {
            handleHistory(req, resp);
        } else if (pathInfo.equals("/tables")) {
            handleListTables(resp);
        } else if (pathInfo.equals("/table-info")) {
            handleTableInfo(req, resp);
        } else {
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();

        // Public inbound endpoint
        if (pathInfo != null && pathInfo.startsWith("/inbound/")) {
            handleInbound(req, resp, pathInfo.substring("/inbound/".length()));
            return;
        }

        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/configs")) {
            handleCreate(req, resp, admin);
        } else {
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");

        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.equals("/toggle-active")) {
            handleToggleActive(req, resp);
        } else {
            handleUpdate(req, resp);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");

        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        handleDelete(req, resp);
    }

    // --- Admin handlers ---

    private void handleListConfigs(HttpServletResponse resp) throws IOException {
        String sql = "SELECT wc.*, u.full_name AS creator_name " +
                     "FROM webhook_configs wc " +
                     "LEFT JOIN users u ON u.id = wc.created_by " +
                     "ORDER BY wc.name";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                appendWebhookJson(json, rs);
            }
            json.append("]");
            resp.getWriter().write(json.toString());

        } catch (SQLException e) {
            log("Error listing webhook configs: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleCreate(HttpServletRequest req, HttpServletResponse resp, User admin)
            throws IOException {
        String body = readBody(req);

        String name = JsonUtil.extractJsonString(body, "name");
        String authType = JsonUtil.extractJsonString(body, "authType");
        String authConfig = JsonUtil.extractJsonObject(body, "authConfig");
        String targetTable = JsonUtil.extractJsonString(body, "targetTable");
        String idFieldSource = JsonUtil.extractJsonString(body, "idFieldSource");
        String idFieldTarget = JsonUtil.extractJsonString(body, "idFieldTarget");
        String fieldMappings = JsonUtil.extractJsonArray(body, "fieldMappings");
        boolean updateOnly = JsonUtil.extractJsonBoolean(body, "updateOnly");

        if (name == null || targetTable == null || idFieldSource == null || idFieldTarget == null) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Missing required fields\"}");
            return;
        }

        // Validate target table
        if (!SyncExecutor.getAllowedTables().contains(targetTable.toLowerCase())) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Table not allowed\"}");
            return;
        }

        // Generate unique URL token
        String urlToken = generateToken();

        String sql = "INSERT INTO webhook_configs (name, url_token, auth_type, auth_config, " +
                     "target_table, id_field_source, id_field_target, field_mappings, " +
                     "update_only, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, urlToken);
            ps.setString(3, authType != null ? authType : "none");
            ps.setString(4, CryptoUtil.encrypt(authConfig));
            ps.setString(5, targetTable);
            ps.setString(6, idFieldSource);
            ps.setString(7, idFieldTarget);
            ps.setString(8, fieldMappings != null ? fieldMappings : "[]");
            ps.setBoolean(9, updateOnly);
            ps.setInt(10, admin.getId());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                int newId = keys.next() ? keys.getInt(1) : 0;
                AuditUtil.logEvent(AuditUtil.SYNC_CONFIG_CHANGE, admin.getId(), req,
                        "webhook_config", String.valueOf(newId), "Created webhook: " + name);
                resp.getWriter().write("{\"success\":true,\"id\":" + newId + ",\"urlToken\":" + JsonUtil.quote(urlToken) + "}");
            }
        } catch (SQLException e) {
            log("Error creating webhook: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleUpdate(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String body = readBody(req);
        int id = JsonUtil.extractJsonInt(body, "id");
        if (id <= 0) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Missing id\"}");
            return;
        }

        String name = JsonUtil.extractJsonString(body, "name");
        String authType = JsonUtil.extractJsonString(body, "authType");
        String authConfig = JsonUtil.extractJsonObject(body, "authConfig");
        String targetTable = JsonUtil.extractJsonString(body, "targetTable");
        String idFieldSource = JsonUtil.extractJsonString(body, "idFieldSource");
        String idFieldTarget = JsonUtil.extractJsonString(body, "idFieldTarget");
        String fieldMappings = JsonUtil.extractJsonArray(body, "fieldMappings");
        boolean updateOnly = JsonUtil.extractJsonBoolean(body, "updateOnly");

        if (targetTable != null && !SyncExecutor.getAllowedTables().contains(targetTable.toLowerCase())) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Table not allowed\"}");
            return;
        }

        String sql = "UPDATE webhook_configs SET name=?, auth_type=?, auth_config=?, " +
                     "target_table=?, id_field_source=?, id_field_target=?, " +
                     "field_mappings=?, update_only=? WHERE id=?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, authType != null ? authType : "none");
            ps.setString(3, CryptoUtil.encrypt(authConfig));
            ps.setString(4, targetTable);
            ps.setString(5, idFieldSource);
            ps.setString(6, idFieldTarget);
            ps.setString(7, fieldMappings != null ? fieldMappings : "[]");
            ps.setBoolean(8, updateOnly);
            ps.setInt(9, id);
            ps.executeUpdate();

            User adminUser = (User) req.getSession().getAttribute("user");
            AuditUtil.logEvent(AuditUtil.SYNC_CONFIG_CHANGE,
                    adminUser != null ? adminUser.getId() : null, req,
                    "webhook_config", String.valueOf(id), "Updated webhook: " + name);

            resp.getWriter().write("{\"success\":true}");
        } catch (SQLException e) {
            log("Error updating webhook: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String idParam = req.getParameter("id");
        if (idParam == null) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Missing id\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM webhook_configs WHERE id=?")) {
            ps.setInt(1, Integer.parseInt(idParam));
            ps.executeUpdate();

            User adminUser = (User) req.getSession().getAttribute("user");
            AuditUtil.logEvent(AuditUtil.SYNC_CONFIG_CHANGE,
                    adminUser != null ? adminUser.getId() : null, req,
                    "webhook_config", idParam, "Deleted webhook id=" + idParam);

            resp.getWriter().write("{\"success\":true}");
        } catch (SQLException e) {
            log("Error deleting webhook: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleToggleActive(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readBody(req);
        int id = JsonUtil.extractJsonInt(body, "id");
        boolean active = JsonUtil.extractJsonBoolean(body, "isActive");

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE webhook_configs SET is_active=? WHERE id=?")) {
            ps.setBoolean(1, active);
            ps.setInt(2, id);
            ps.executeUpdate();
            resp.getWriter().write("{\"success\":true}");
        } catch (SQLException e) {
            log("Error toggling webhook: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleHistory(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String webhookId = req.getParameter("webhookId");
        if (webhookId == null) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Missing webhookId\"}");
            return;
        }

        String sql = "SELECT * FROM webhook_run_history WHERE webhook_id=? ORDER BY received_at DESC LIMIT 50";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(webhookId));

            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{");
                    json.append("\"id\":").append(rs.getLong("id")).append(",");
                    json.append("\"receivedAt\":").append(JsonUtil.quote(rs.getTimestamp("received_at").toString())).append(",");
                    json.append("\"status\":").append(JsonUtil.quote(rs.getString("status"))).append(",");
                    json.append("\"recordsReceived\":").append(rs.getInt("records_received")).append(",");
                    json.append("\"recordsUpserted\":").append(rs.getInt("records_upserted")).append(",");
                    json.append("\"recordsFailed\":").append(rs.getInt("records_failed")).append(",");
                    json.append("\"errorMessage\":").append(JsonUtil.quote(rs.getString("error_message"))).append(",");
                    json.append("\"sourceIp\":").append(JsonUtil.quote(rs.getString("source_ip")));
                    json.append("}");
                }
                json.append("]");
                resp.getWriter().write(json.toString());
            }
        } catch (SQLException e) {
            log("Error loading webhook history: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleListTables(HttpServletResponse resp) throws IOException {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (String t : SyncExecutor.getAllowedTables()) {
            if (!first) json.append(",");
            first = false;
            json.append(JsonUtil.quote(t));
        }
        json.append("]");
        resp.getWriter().write(json.toString());
    }

    private void handleTableInfo(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String table = req.getParameter("table");
        if (table == null || !SyncExecutor.getAllowedTables().contains(table.toLowerCase())) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Invalid table\"}");
            return;
        }

        try {
            List<SyncExecutor.ColumnInfo> columns = SyncExecutor.getTableColumns(table);
            StringBuilder json = new StringBuilder("{\"columns\":[");
            boolean first = true;
            for (SyncExecutor.ColumnInfo col : columns) {
                if (!first) json.append(",");
                first = false;
                json.append("{\"name\":").append(JsonUtil.quote(col.name))
                    .append(",\"type\":").append(JsonUtil.quote(col.type))
                    .append(",\"nullable\":").append(col.nullable)
                    .append(",\"isKey\":").append(col.isKey).append("}");
            }
            json.append("]}");
            resp.getWriter().write(json.toString());
        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // --- Public inbound handler ---

    private void handleInbound(HttpServletRequest req, HttpServletResponse resp, String token)
            throws IOException {
        if (token == null || token.isEmpty()) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Missing token\"}");
            return;
        }

        String sourceIp = req.getRemoteAddr();
        String forwardedFor = req.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            sourceIp = forwardedFor.split(",")[0].trim();
        }

        // Load webhook config
        String configSql = "SELECT * FROM webhook_configs WHERE url_token=? AND is_active=1";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(configSql)) {
            ps.setString(1, token);

            String name, authType, authConfigEnc, targetTable, idFieldSource, idFieldTarget,
                   fieldMappingsJson;
            boolean updateOnly;
            int webhookId;

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    resp.setStatus(404);
                    resp.getWriter().write("{\"error\":\"Webhook not found or inactive\"}");
                    return;
                }

                webhookId = rs.getInt("id");
                name = rs.getString("name");
                authType = rs.getString("auth_type");
                authConfigEnc = rs.getString("auth_config");
                targetTable = rs.getString("target_table");
                idFieldSource = rs.getString("id_field_source");
                idFieldTarget = rs.getString("id_field_target");
                fieldMappingsJson = rs.getString("field_mappings");
                updateOnly = rs.getBoolean("update_only");
            }

            // Validate auth
            if (!validateInboundAuth(req, authType, authConfigEnc)) {
                saveHistory(conn, webhookId, "failed", 0, 0, 0, "Authentication failed", sourceIp);
                resp.setStatus(401);
                resp.getWriter().write("{\"error\":\"Authentication failed\"}");
                return;
            }

            // Read and parse body
            String body = readBody(req);
            if (body == null || body.trim().isEmpty()) {
                saveHistory(conn, webhookId, "failed", 0, 0, 0, "Empty body", sourceIp);
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Empty body\"}");
                return;
            }

            // Parse JSON objects — single object or array
            List<String> objects;
            String trimmed = body.trim();
            if (trimmed.startsWith("[")) {
                objects = SyncExecutor.extractJsonObjects(trimmed);
            } else if (trimmed.startsWith("{")) {
                objects = new ArrayList<>();
                objects.add(trimmed);
            } else {
                saveHistory(conn, webhookId, "failed", 0, 0, 0, "Invalid JSON", sourceIp);
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Invalid JSON — expected object or array\"}");
                return;
            }

            int received = objects.size();
            int upserted = 0;
            int failed = 0;
            String lastError = null;

            // Validate table
            if (!SyncExecutor.getAllowedTables().contains(targetTable.toLowerCase())) {
                saveHistory(conn, webhookId, "failed", received, 0, 0, "Table not allowed: " + targetTable, sourceIp);
                resp.setStatus(500);
                resp.getWriter().write("{\"error\":\"Webhook misconfigured — table not allowed\"}");
                return;
            }

            // Parse mappings
            List<String[]> mappings = SyncExecutor.parseMappings(fieldMappingsJson);

            // Validate columns
            List<String> allColumns = new ArrayList<>();
            allColumns.add(idFieldTarget);
            for (String[] m : mappings) allColumns.add(m[1]);
            try {
                SyncExecutor.validateColumns(conn, targetTable, allColumns);
            } catch (Exception e) {
                saveHistory(conn, webhookId, "failed", received, 0, 0, "Column validation: " + e.getMessage(), sourceIp);
                resp.setStatus(500);
                resp.getWriter().write("{\"error\":\"Column validation failed\"}");
                return;
            }

            // Build SQL
            String sql;
            try {
                if (updateOnly) {
                    sql = SyncExecutor.buildUpdateSql(targetTable, idFieldTarget, mappings);
                } else {
                    sql = SyncExecutor.buildUpsertSql(targetTable, idFieldTarget, mappings);
                }
            } catch (Exception e) {
                saveHistory(conn, webhookId, "failed", received, 0, 0, "SQL build: " + e.getMessage(), sourceIp);
                resp.setStatus(500);
                resp.getWriter().write("{\"error\":\"SQL build failed\"}");
                return;
            }

            // Process records
            try (PreparedStatement upsertPs = conn.prepareStatement(sql)) {
                for (String obj : objects) {
                    try {
                        // Extract ID value
                        String[] idParsed = SyncExecutor.parseFieldTransform(idFieldSource);
                        String idValue = SyncExecutor.extractJsonFieldValue(obj, idParsed[0]);
                        if (idParsed[1] != null) idValue = SyncExecutor.applyTransform(idValue, idParsed[1]);

                        if (idValue == null || idValue.isEmpty()) {
                            failed++;
                            lastError = "Missing ID field: " + idFieldSource;
                            continue;
                        }

                        if (updateOnly) {
                            // UPDATE: SET values first, WHERE id last
                            int paramIdx = 1;
                            for (String[] m : mappings) {
                                String[] parsed = SyncExecutor.parseFieldTransform(m[0]);
                                String val = SyncExecutor.extractJsonFieldValue(obj, parsed[0]);
                                if (parsed[1] != null) val = SyncExecutor.applyTransform(val, parsed[1]);
                                upsertPs.setString(paramIdx++, val);
                            }
                            upsertPs.setString(paramIdx, idValue);
                        } else {
                            // UPSERT: id first, then mapped columns
                            int paramIdx = 1;
                            upsertPs.setString(paramIdx++, idValue);
                            for (String[] m : mappings) {
                                String[] parsed = SyncExecutor.parseFieldTransform(m[0]);
                                String val = SyncExecutor.extractJsonFieldValue(obj, parsed[0]);
                                if (parsed[1] != null) val = SyncExecutor.applyTransform(val, parsed[1]);
                                upsertPs.setString(paramIdx++, val);
                            }
                        }

                        int affected = upsertPs.executeUpdate();
                        if (affected > 0) upserted++;

                    } catch (Exception e) {
                        failed++;
                        lastError = e.getMessage();
                    }
                }
            }

            // Update last_received_at
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE webhook_configs SET last_received_at=NOW(), last_received_count=? WHERE id=?")) {
                upd.setInt(1, received);
                upd.setInt(2, webhookId);
                upd.executeUpdate();
            }

            // Save history
            String status = (failed == 0) ? "success" : (upserted > 0 ? "success" : "failed");
            saveHistory(conn, webhookId, status, received, upserted, failed, lastError, sourceIp);

            resp.getWriter().write("{\"success\":true,\"received\":" + received +
                    ",\"upserted\":" + upserted + ",\"failed\":" + failed + "}");

        } catch (SQLException e) {
            log("Webhook inbound error: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // --- Auth validation ---

    private boolean validateInboundAuth(HttpServletRequest req, String authType, String authConfigEnc) {
        if ("none".equals(authType)) return true;

        String authConfig = authConfigEnc != null ? CryptoUtil.decrypt(authConfigEnc) : null;
        if (authConfig == null || authConfig.isEmpty()) return true;

        if ("api_key".equals(authType)) {
            String expectedKey = SyncExecutor.extractJsonFieldValue(authConfig, "keyValue");
            String submittedKey = req.getHeader("X-API-Key");
            if (expectedKey == null || submittedKey == null) return false;
            return MessageDigest.isEqual(
                    expectedKey.getBytes(StandardCharsets.UTF_8),
                    submittedKey.getBytes(StandardCharsets.UTF_8));
        }

        if ("bearer".equals(authType)) {
            String expectedToken = SyncExecutor.extractJsonFieldValue(authConfig, "token");
            String authHeader = req.getHeader("Authorization");
            if (expectedToken == null || authHeader == null || !authHeader.startsWith("Bearer ")) return false;
            String submittedToken = authHeader.substring(7).trim();
            return MessageDigest.isEqual(
                    expectedToken.getBytes(StandardCharsets.UTF_8),
                    submittedToken.getBytes(StandardCharsets.UTF_8));
        }

        return false;
    }

    // --- Helpers ---

    private void saveHistory(Connection conn, int webhookId, String status,
                            int received, int upserted, int failed,
                            String errorMessage, String sourceIp) {
        String sql = "INSERT INTO webhook_run_history " +
                     "(webhook_id, status, records_received, records_upserted, records_failed, error_message, source_ip) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, webhookId);
            ps.setString(2, status);
            ps.setInt(3, received);
            ps.setInt(4, upserted);
            ps.setInt(5, failed);
            ps.setString(6, errorMessage);
            ps.setString(7, sourceIp);
            ps.executeUpdate();
        } catch (SQLException e) {
            log("Error saving webhook history: " + e.getMessage());
        }
    }

    private void appendWebhookJson(StringBuilder json, ResultSet rs) throws SQLException {
        json.append("{");
        json.append("\"id\":").append(rs.getInt("id")).append(",");
        json.append("\"name\":").append(JsonUtil.quote(rs.getString("name"))).append(",");
        json.append("\"urlToken\":").append(JsonUtil.quote(rs.getString("url_token"))).append(",");
        json.append("\"authType\":").append(JsonUtil.quote(rs.getString("auth_type"))).append(",");
        String authConfigRaw = rs.getString("auth_config");
        String authConfigDecrypted = authConfigRaw != null ? CryptoUtil.decrypt(authConfigRaw) : null;
        json.append("\"authConfig\":").append(authConfigDecrypted != null ? authConfigDecrypted : "null").append(",");
        json.append("\"targetTable\":").append(JsonUtil.quote(rs.getString("target_table"))).append(",");
        json.append("\"idFieldSource\":").append(JsonUtil.quote(rs.getString("id_field_source"))).append(",");
        json.append("\"idFieldTarget\":").append(JsonUtil.quote(rs.getString("id_field_target"))).append(",");
        json.append("\"fieldMappings\":").append(rs.getString("field_mappings") != null ? rs.getString("field_mappings") : "[]").append(",");
        json.append("\"updateOnly\":").append(rs.getBoolean("update_only")).append(",");
        json.append("\"isActive\":").append(rs.getBoolean("is_active")).append(",");

        Timestamp lastReceived = rs.getTimestamp("last_received_at");
        json.append("\"lastReceivedAt\":").append(JsonUtil.quote(lastReceived != null ? lastReceived.toString() : null)).append(",");
        json.append("\"lastReceivedCount\":").append(rs.getInt("last_received_count")).append(",");
        json.append("\"creatorName\":").append(JsonUtil.quote(rs.getString("creator_name")));
        json.append("}");
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        StringBuilder hex = new StringBuilder(64);
        for (byte b : bytes) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static String readBody(HttpServletRequest req) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        return body.toString();
    }
}
