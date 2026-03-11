package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.AdminUtil;
import com.infocaption.dashboard.util.AuditUtil;
import com.infocaption.dashboard.util.CryptoUtil;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.JsonUtil;
import com.infocaption.dashboard.util.SyncExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.*;
import java.util.List;

/**
 * Sync Configuration API Servlet — CRUD, test, run, table info.
 *
 * GET    /api/sync/configs           — List all sync configs
 * POST   /api/sync/configs           — Create new config
 * PUT    /api/sync/configs           — Update config
 * DELETE /api/sync/configs?id=N      — Delete config
 * POST   /api/sync/test              — Test URL + auth, return JSON fields
 * POST   /api/sync/run?configId=N    — Manually trigger a sync
 * GET    /api/sync/table-info?table=X — Get columns for a table
 * GET    /api/sync/tables            — Get allowed table names
 * GET    /api/sync/history?configId=N — Get run history
 */
public class SyncConfigServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(SyncConfigServlet.class);

    @Override
    public void init() throws ServletException {
        // Auto-create tables if missing (idempotent)
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS sync_configs (" +
                "  id              INT AUTO_INCREMENT PRIMARY KEY," +
                "  name            VARCHAR(255) NOT NULL," +
                "  source_url      VARCHAR(2000) NOT NULL," +
                "  auth_type       ENUM('none','api_key','bearer','basic') NOT NULL DEFAULT 'none'," +
                "  auth_config     TEXT NULL," +
                "  json_root_path  VARCHAR(500) NULL," +
                "  target_table    VARCHAR(100) NOT NULL," +
                "  id_field_source VARCHAR(255) NOT NULL," +
                "  id_field_target VARCHAR(255) NOT NULL," +
                "  field_mappings  TEXT NOT NULL," +
                "  schedule_minutes INT NOT NULL DEFAULT 0," +
                "  update_only     TINYINT(1) NOT NULL DEFAULT 0," +
                "  is_active       TINYINT(1) NOT NULL DEFAULT 1," +
                "  last_run_at     TIMESTAMP NULL," +
                "  last_run_status VARCHAR(50) NULL," +
                "  last_run_count  INT NULL DEFAULT 0," +
                "  created_by      INT NOT NULL," +
                "  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  CONSTRAINT fk_sync_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS sync_run_history (" +
                "  id                BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  config_id         INT NOT NULL," +
                "  started_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  completed_at      TIMESTAMP NULL," +
                "  status            ENUM('running','success','failed') DEFAULT 'running'," +
                "  records_fetched   INT DEFAULT 0," +
                "  records_upserted  INT DEFAULT 0," +
                "  records_failed    INT DEFAULT 0," +
                "  error_message     TEXT NULL," +
                "  triggered_by      INT NULL," +
                "  CONSTRAINT fk_synchistory_config FOREIGN KEY (config_id) REFERENCES sync_configs(id) ON DELETE CASCADE," +
                "  CONSTRAINT fk_synchistory_user FOREIGN KEY (triggered_by) REFERENCES users(id) ON DELETE SET NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            log("Sync tables verified/created successfully");
        } catch (SQLException e) {
            log("Warning: Could not auto-create sync tables: " + e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/configs")) {
            handleListConfigs(resp);
        } else if (pathInfo.equals("/table-info")) {
            handleTableInfo(req, resp);
        } else if (pathInfo.equals("/tables")) {
            handleListTables(resp);
        } else if (pathInfo.equals("/history")) {
            handleHistory(req, resp);
        } else {
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.equals("/test")) {
            handleTest(req, resp);
        } else if (pathInfo != null && pathInfo.equals("/run")) {
            handleRun(req, resp, admin);
        } else if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/configs")) {
            handleCreate(req, resp, admin);
        } else {
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        resp.setContentType("application/json; charset=UTF-8");
        handleUpdate(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        resp.setContentType("application/json; charset=UTF-8");
        handleDelete(req, resp);
    }

    // --- Handlers ---

    private void handleListConfigs(HttpServletResponse resp) throws IOException {
        String sql = "SELECT sc.*, u.full_name AS creator_name " +
                     "FROM sync_configs sc " +
                     "LEFT JOIN users u ON u.id = sc.created_by " +
                     "ORDER BY sc.name";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                appendConfigJson(json, rs);
            }
            json.append("]");
            resp.getWriter().write(json.toString());

        } catch (SQLException e) {
            log("Error listing sync configs: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleCreate(HttpServletRequest req, HttpServletResponse resp, User admin)
            throws IOException {
        String body = readBody(req);

        String name = JsonUtil.extractJsonString(body, "name");
        String sourceUrl = JsonUtil.extractJsonString(body, "sourceUrl");
        String authType = JsonUtil.extractJsonString(body, "authType");
        String authConfig = JsonUtil.extractJsonObject(body, "authConfig");
        String jsonRootPath = JsonUtil.extractJsonString(body, "jsonRootPath");
        String targetTable = JsonUtil.extractJsonString(body, "targetTable");
        String idFieldSource = JsonUtil.extractJsonString(body, "idFieldSource");
        String idFieldTarget = JsonUtil.extractJsonString(body, "idFieldTarget");
        String fieldMappings = JsonUtil.extractJsonArray(body, "fieldMappings");
        int scheduleMinutes = JsonUtil.extractJsonInt(body, "scheduleMinutes");
        boolean updateOnly = JsonUtil.extractJsonBoolean(body, "updateOnly");

        if (name == null || sourceUrl == null || targetTable == null ||
            idFieldSource == null || idFieldTarget == null) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Missing required fields\"}");
            return;
        }

        String sql = "INSERT INTO sync_configs (name, source_url, auth_type, auth_config, " +
                     "json_root_path, target_table, id_field_source, id_field_target, " +
                     "field_mappings, schedule_minutes, update_only, created_by) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, sourceUrl);
            ps.setString(3, authType != null ? authType : "none");
            ps.setString(4, CryptoUtil.encrypt(authConfig));
            ps.setString(5, jsonRootPath);
            ps.setString(6, targetTable);
            ps.setString(7, idFieldSource);
            ps.setString(8, idFieldTarget);
            ps.setString(9, fieldMappings != null ? fieldMappings : "[]");
            ps.setInt(10, scheduleMinutes);
            ps.setBoolean(11, updateOnly);
            ps.setInt(12, admin.getId());
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            int newId = keys.next() ? keys.getInt(1) : 0;

            AuditUtil.logEvent(AuditUtil.SYNC_CONFIG_CHANGE, admin.getId(), req, "sync_config", String.valueOf(newId),
                    "Created sync config: " + name);

            resp.getWriter().write("{\"success\":true,\"id\":" + newId + "}");

        } catch (SQLException e) {
            log("Error creating sync config: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleUpdate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readBody(req);
        int id = JsonUtil.extractJsonInt(body, "id");
        if (id <= 0) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Invalid id\"}");
            return;
        }

        String name = JsonUtil.extractJsonString(body, "name");
        String sourceUrl = JsonUtil.extractJsonString(body, "sourceUrl");
        String authType = JsonUtil.extractJsonString(body, "authType");
        String authConfig = JsonUtil.extractJsonObject(body, "authConfig");
        String jsonRootPath = JsonUtil.extractJsonString(body, "jsonRootPath");
        String targetTable = JsonUtil.extractJsonString(body, "targetTable");
        String idFieldSource = JsonUtil.extractJsonString(body, "idFieldSource");
        String idFieldTarget = JsonUtil.extractJsonString(body, "idFieldTarget");
        String fieldMappings = JsonUtil.extractJsonArray(body, "fieldMappings");
        int scheduleMinutes = JsonUtil.extractJsonInt(body, "scheduleMinutes");
        boolean updateOnly = JsonUtil.extractJsonBoolean(body, "updateOnly");
        // is_active: check both "isActive" and default to keeping current
        String isActiveStr = JsonUtil.extractJsonString(body, "isActive");

        String sql = "UPDATE sync_configs SET name=?, source_url=?, auth_type=?, auth_config=?, " +
                     "json_root_path=?, target_table=?, id_field_source=?, id_field_target=?, " +
                     "field_mappings=?, schedule_minutes=?, update_only=?" +
                     (isActiveStr != null ? ", is_active=?" : "") +
                     " WHERE id=?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            ps.setString(idx++, name);
            ps.setString(idx++, sourceUrl);
            ps.setString(idx++, authType != null ? authType : "none");
            ps.setString(idx++, CryptoUtil.encrypt(authConfig));
            ps.setString(idx++, jsonRootPath);
            ps.setString(idx++, targetTable);
            ps.setString(idx++, idFieldSource);
            ps.setString(idx++, idFieldTarget);
            ps.setString(idx++, fieldMappings != null ? fieldMappings : "[]");
            ps.setInt(idx++, scheduleMinutes);
            ps.setBoolean(idx++, updateOnly);
            if (isActiveStr != null) {
                ps.setBoolean(idx++, "true".equals(isActiveStr));
            }
            ps.setInt(idx, id);
            int rows = ps.executeUpdate();

            User adminUser = (User) req.getSession().getAttribute("user");
            AuditUtil.logEvent(AuditUtil.SYNC_CONFIG_CHANGE, adminUser != null ? adminUser.getId() : null, req, "sync_config", String.valueOf(id),
                    "Updated sync config: " + name);

            resp.getWriter().write("{\"success\":true,\"rowsAffected\":" + rows + "}");

        } catch (SQLException e) {
            log("Error updating sync config: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String idParam = req.getParameter("id");
        if (idParam == null) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Missing id parameter\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM sync_configs WHERE id = ?")) {
            ps.setInt(1, Integer.parseInt(idParam));
            int rows = ps.executeUpdate();

            User adminUser = (User) req.getSession().getAttribute("user");
            AuditUtil.logEvent(AuditUtil.SYNC_CONFIG_CHANGE, adminUser != null ? adminUser.getId() : null, req, "sync_config", idParam,
                    "Deleted sync config id=" + idParam);

            resp.getWriter().write("{\"success\":true,\"rowsAffected\":" + rows + "}");
        } catch (SQLException e) {
            log("Error deleting sync config: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleTest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String body = readBody(req);
            String url = JsonUtil.extractJsonString(body, "sourceUrl");
            String authType = JsonUtil.extractJsonString(body, "authType");
            String authConfig = JsonUtil.extractJsonObject(body, "authConfig");
            String rootPath = JsonUtil.extractJsonString(body, "jsonRootPath");

            if (url == null || url.isEmpty()) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Missing sourceUrl\"}");
                return;
            }

            log("Testing connection to: " + url + " (authType=" + authType + ", rootPath=" + rootPath + ")");

            SyncExecutor.TestResult result = SyncExecutor.testConnection(url, authType, authConfig);

            // If we have a root path, try navigating and re-detecting fields
            if (result.success && rootPath != null && !rootPath.isEmpty()) {
                try {
                    // Re-fetch with full body for root path navigation
                    String fullBody = SyncExecutor.fetchUrl(url, authType, authConfig);
                    String navigated = SyncExecutor.navigateJsonPath(fullBody, rootPath);
                    result.fields = SyncExecutor.extractFieldNamesFromFirstObject(navigated);
                    result.sampleCount = SyncExecutor.extractJsonObjects(navigated).size();
                } catch (Exception e) {
                    log("Root path navigation failed: " + e.getMessage());
                    // Keep original fields if navigation fails
                }
            }

            StringBuilder json = new StringBuilder("{");
            json.append("\"success\":").append(result.success).append(",");
            json.append("\"statusCode\":").append(result.statusCode).append(",");
            json.append("\"error\":").append(JsonUtil.quote(result.error)).append(",");
            json.append("\"sampleCount\":").append(result.sampleCount).append(",");
            json.append("\"fields\":[");
            for (int i = 0; i < result.fields.size(); i++) {
                if (i > 0) json.append(",");
                json.append(JsonUtil.quote(result.fields.get(i)));
            }
            json.append("]}");
            resp.getWriter().write(json.toString());

        } catch (Exception e) {
            log.error("Failed to test sync connection", e);
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Server error\"}");
        }
    }

    private void handleRun(HttpServletRequest req, HttpServletResponse resp, User admin)
            throws IOException {
        String configIdParam = null;
        try {
            configIdParam = req.getParameter("configId");
            if (configIdParam == null) {
                // Try reading from body
                String body = readBody(req);
                configIdParam = String.valueOf(JsonUtil.extractJsonInt(body, "configId"));
            }

            int configId;
            try {
                configId = Integer.parseInt(configIdParam);
            } catch (NumberFormatException e) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Invalid configId\"}");
                return;
            }

            long historyId = SyncExecutor.execute(configId, admin.getId());

            AuditUtil.logEvent(AuditUtil.SYNC_RUN, admin.getId(), req, "sync_config", String.valueOf(configId),
                    "Manual sync run triggered, historyId=" + historyId);

            resp.getWriter().write("{\"success\":true,\"historyId\":" + historyId + "}");

        } catch (Exception e) {
            log.error("Failed to execute sync run for configId={}", configIdParam, e);
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Run failed\"}");
        }
    }

    private void handleTableInfo(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String table = req.getParameter("table");
        if (table == null) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Missing table parameter\"}");
            return;
        }

        try {
            List<SyncExecutor.ColumnInfo> columns = SyncExecutor.getTableColumns(table);

            StringBuilder json = new StringBuilder("{\"table\":");
            json.append(JsonUtil.quote(table)).append(",\"columns\":[");

            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) json.append(",");
                SyncExecutor.ColumnInfo col = columns.get(i);
                json.append("{");
                json.append("\"name\":").append(JsonUtil.quote(col.name)).append(",");
                json.append("\"type\":").append(JsonUtil.quote(col.type)).append(",");
                json.append("\"nullable\":").append(col.nullable).append(",");
                json.append("\"isKey\":").append(col.isKey);
                json.append("}");
            }

            json.append("]}");
            resp.getWriter().write(json.toString());

        } catch (IllegalArgumentException e) {
            resp.setStatus(403);
            resp.getWriter().write("{\"error\":\"Table not allowed\"}");
        } catch (SQLException e) {
            log("Error getting table info: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleListTables(HttpServletResponse resp) throws IOException {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (String table : SyncExecutor.getAllowedTables()) {
            if (!first) json.append(",");
            first = false;
            json.append(JsonUtil.quote(table));
        }
        json.append("]");
        resp.getWriter().write(json.toString());
    }

    private void handleHistory(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String configIdParam = req.getParameter("configId");
        if (configIdParam == null) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Missing configId\"}");
            return;
        }

        String sql = "SELECT h.*, u.full_name AS triggered_by_name " +
                     "FROM sync_run_history h " +
                     "LEFT JOIN users u ON u.id = h.triggered_by " +
                     "WHERE h.config_id = ? " +
                     "ORDER BY h.started_at DESC LIMIT 50";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(configIdParam));
            ResultSet rs = ps.executeQuery();

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;

                json.append("{");
                json.append("\"id\":").append(rs.getLong("id")).append(",");
                json.append("\"configId\":").append(rs.getInt("config_id")).append(",");

                Timestamp startedAt = rs.getTimestamp("started_at");
                json.append("\"startedAt\":").append(JsonUtil.quote(startedAt != null ? startedAt.toString() : null)).append(",");

                Timestamp completedAt = rs.getTimestamp("completed_at");
                json.append("\"completedAt\":").append(JsonUtil.quote(completedAt != null ? completedAt.toString() : null)).append(",");

                json.append("\"status\":").append(JsonUtil.quote(rs.getString("status"))).append(",");
                json.append("\"recordsFetched\":").append(rs.getInt("records_fetched")).append(",");
                json.append("\"recordsUpserted\":").append(rs.getInt("records_upserted")).append(",");
                json.append("\"recordsFailed\":").append(rs.getInt("records_failed")).append(",");
                json.append("\"errorMessage\":").append(JsonUtil.quote(rs.getString("error_message"))).append(",");
                json.append("\"triggeredByName\":").append(JsonUtil.quote(rs.getString("triggered_by_name")));
                json.append("}");
            }
            json.append("]");
            resp.getWriter().write(json.toString());

        } catch (SQLException e) {
            log("Error getting sync history: " + e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // --- JSON helpers ---

    private void appendConfigJson(StringBuilder json, ResultSet rs) throws SQLException {
        json.append("{");
        json.append("\"id\":").append(rs.getInt("id")).append(",");
        json.append("\"name\":").append(JsonUtil.quote(rs.getString("name"))).append(",");
        json.append("\"sourceUrl\":").append(JsonUtil.quote(rs.getString("source_url"))).append(",");
        json.append("\"authType\":").append(JsonUtil.quote(rs.getString("auth_type"))).append(",");
        String authConfigRaw = rs.getString("auth_config");
        String authConfigDecrypted = authConfigRaw != null ? CryptoUtil.decrypt(authConfigRaw) : null;
        json.append("\"authConfig\":").append(authConfigDecrypted != null ? authConfigDecrypted : "null").append(",");
        json.append("\"jsonRootPath\":").append(JsonUtil.quote(rs.getString("json_root_path"))).append(",");
        json.append("\"targetTable\":").append(JsonUtil.quote(rs.getString("target_table"))).append(",");
        json.append("\"idFieldSource\":").append(JsonUtil.quote(rs.getString("id_field_source"))).append(",");
        json.append("\"idFieldTarget\":").append(JsonUtil.quote(rs.getString("id_field_target"))).append(",");
        json.append("\"fieldMappings\":").append(rs.getString("field_mappings") != null ? rs.getString("field_mappings") : "[]").append(",");
        json.append("\"scheduleMinutes\":").append(rs.getInt("schedule_minutes")).append(",");
        json.append("\"updateOnly\":").append(rs.getBoolean("update_only")).append(",");
        json.append("\"isActive\":").append(rs.getBoolean("is_active")).append(",");

        Timestamp lastRunAt = rs.getTimestamp("last_run_at");
        json.append("\"lastRunAt\":").append(JsonUtil.quote(lastRunAt != null ? lastRunAt.toString() : null)).append(",");
        json.append("\"lastRunStatus\":").append(JsonUtil.quote(rs.getString("last_run_status"))).append(",");
        json.append("\"lastRunCount\":").append(rs.getInt("last_run_count")).append(",");
        json.append("\"creatorName\":").append(JsonUtil.quote(rs.getString("creator_name")));
        json.append("}");
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
