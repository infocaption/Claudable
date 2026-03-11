package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.JsonUtil;
import com.infocaption.dashboard.util.WebPushUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * CloudGuard Incident Reporting API.
 *
 * POST /api/cloudguard/report   (PUBLIC)  — Report an incident, returns incident ID
 * POST /api/cloudguard/resolve  (PUBLIC)  — Resolve an incident by ID (sets is_active = 0)
 * GET  /api/cloudguard/active   (AUTH)    — List all active incidents (requires Bearer token or session)
 *
 * Public endpoints require no authentication — external scripts and monitoring agents
 * can report and resolve incidents freely. The /active endpoint requires authentication
 * since it exposes the full incident list.
 */
public class CloudGuardApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(CloudGuardApiServlet.class);

    private static final Set<String> VALID_SEVERITIES = new HashSet<>(
        Arrays.asList("info", "warning", "error", "critical")
    );

    // ==================== Init ====================

    @Override
    public void init() throws ServletException {
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS cloudguard (" +
                "  id              INT AUTO_INCREMENT PRIMARY KEY," +
                "  entity_name     VARCHAR(500) NOT NULL," +
                "  entity_type     VARCHAR(50)  NOT NULL DEFAULT 'service'," +
                "  severity        VARCHAR(20)  NOT NULL DEFAULT 'error'," +
                "  message         TEXT         NULL," +
                "  reported_by     VARCHAR(255) NULL," +
                "  reported_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  resolved_at     TIMESTAMP    NULL," +
                "  resolved_by     VARCHAR(255) NULL," +
                "  resolution_note TEXT         NULL," +
                "  is_active       TINYINT(1)   NOT NULL DEFAULT 1," +
                "  INDEX idx_cg_active   (is_active, severity)," +
                "  INDEX idx_cg_entity   (entity_name(100))," +
                "  INDEX idx_cg_reported (reported_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            log.info("CloudGuard table verified/created successfully");
        } catch (SQLException e) {
            log.warn("Could not auto-create cloudguard table: {}", e.getMessage());
        }
    }

    // ==================== GET — List active incidents (AUTH required) ====================

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        if (pathInfo.equals("/active") || pathInfo.equals("/active/")) {
            // Auth is enforced by AuthFilter — if we get here, user is authenticated
            handleListActive(resp);
        } else if (pathInfo.equals("/history") || pathInfo.equals("/history/")) {
            handleHistory(req, resp);
        } else if (pathInfo.equals("/server-status") || pathInfo.equals("/server-status/")) {
            handleServerStatus(resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    // ==================== POST — Report / Resolve (PUBLIC) ====================

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        if (pathInfo.equals("/report") || pathInfo.equals("/report/")) {
            handleReport(req, resp);
        } else if (pathInfo.equals("/resolve") || pathInfo.equals("/resolve/")) {
            handleResolve(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    // ==================== handleReport — POST /api/cloudguard/report ====================

    private void handleReport(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readRequestBody(req);

        String entityName = JsonUtil.extractJsonString(body, "entityName");
        if (entityName == null || entityName.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"entityName is required\"}");
            return;
        }
        entityName = entityName.trim();
        if (entityName.length() > 500) entityName = entityName.substring(0, 500);

        String entityType = JsonUtil.extractJsonString(body, "entityType");
        if (entityType == null || entityType.trim().isEmpty()) entityType = "service";
        entityType = entityType.trim();
        if (entityType.length() > 50) entityType = entityType.substring(0, 50);

        String severity = JsonUtil.extractJsonString(body, "severity");
        if (severity == null || !VALID_SEVERITIES.contains(severity.toLowerCase())) {
            severity = "error";
        } else {
            severity = severity.toLowerCase();
        }

        String message = JsonUtil.extractJsonString(body, "message");
        String reportedBy = JsonUtil.extractJsonString(body, "reportedBy");
        if (reportedBy != null && reportedBy.length() > 255) reportedBy = reportedBy.substring(0, 255);

        String sql = "INSERT INTO cloudguard (entity_name, entity_type, severity, message, reported_by) " +
                     "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, entityName);
            ps.setString(2, entityType);
            ps.setString(3, severity);
            ps.setString(4, message);
            ps.setString(5, reportedBy);
            ps.executeUpdate();

            int incidentId = 0;
            String reportedAt = null;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) incidentId = keys.getInt(1);
            }

            // Fetch the reported_at timestamp
            try (PreparedStatement ps2 = conn.prepareStatement(
                    "SELECT reported_at FROM cloudguard WHERE id = ?")) {
                ps2.setInt(1, incidentId);
                try (ResultSet rs = ps2.executeQuery()) {
                    if (rs.next()) reportedAt = rs.getString("reported_at");
                }
            }

            log.info("CloudGuard incident reported: id={}, entity={}, severity={}", incidentId, entityName, severity);

            // Trigger push notifications async (does not block the response)
            WebPushUtil.notifySubscribersAsync(incidentId, entityName, severity, message);

            StringBuilder json = new StringBuilder("{");
            json.append("\"id\":").append(incidentId);
            json.append(",\"entityName\":").append(JsonUtil.quote(entityName));
            json.append(",\"entityType\":").append(JsonUtil.quote(entityType));
            json.append(",\"severity\":").append(JsonUtil.quote(severity));
            json.append(",\"reportedAt\":").append(JsonUtil.quote(reportedAt));
            json.append("}");
            resp.getWriter().write(json.toString());

        } catch (SQLException e) {
            log.error("Error reporting CloudGuard incident: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== handleResolve — POST /api/cloudguard/resolve ====================

    private void handleResolve(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readRequestBody(req);

        int incidentId = JsonUtil.extractJsonInt(body, "id");
        if (incidentId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"id is required and must be a positive integer\"}");
            return;
        }

        String resolvedBy = JsonUtil.extractJsonString(body, "resolvedBy");
        if (resolvedBy != null && resolvedBy.length() > 255) resolvedBy = resolvedBy.substring(0, 255);

        String resolutionNote = JsonUtil.extractJsonString(body, "resolutionNote");

        String sql = "UPDATE cloudguard SET is_active = 0, resolved_at = NOW(), " +
                     "resolved_by = ?, resolution_note = ? " +
                     "WHERE id = ? AND is_active = 1";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, resolvedBy);
            ps.setString(2, resolutionNote);
            ps.setInt(3, incidentId);
            int rows = ps.executeUpdate();

            if (rows > 0) {
                // Fetch the resolved_at timestamp
                String resolvedAt = null;
                try (PreparedStatement ps2 = conn.prepareStatement(
                        "SELECT resolved_at FROM cloudguard WHERE id = ?")) {
                    ps2.setInt(1, incidentId);
                    try (ResultSet rs = ps2.executeQuery()) {
                        if (rs.next()) resolvedAt = rs.getString("resolved_at");
                    }
                }

                log.info("CloudGuard incident resolved: id={}", incidentId);

                StringBuilder json = new StringBuilder("{");
                json.append("\"success\":true");
                json.append(",\"id\":").append(incidentId);
                json.append(",\"resolvedAt\":").append(JsonUtil.quote(resolvedAt));
                json.append("}");
                resp.getWriter().write(json.toString());
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                StringBuilder json = new StringBuilder("{");
                json.append("\"error\":\"Incident not found or already resolved\"");
                json.append(",\"id\":").append(incidentId);
                json.append("}");
                resp.getWriter().write(json.toString());
            }

        } catch (SQLException e) {
            log.error("Error resolving CloudGuard incident: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== handleListActive — GET /api/cloudguard/active ====================

    private void handleListActive(HttpServletResponse resp) throws IOException {
        String sql =
            "SELECT id, entity_name, entity_type, severity, message, " +
            "       reported_by, reported_at " +
            "FROM cloudguard " +
            "WHERE is_active = 1 " +
            "ORDER BY FIELD(severity, 'critical', 'error', 'warning', 'info'), reported_at DESC";

        StringBuilder json = new StringBuilder("[");

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;

                json.append("{");
                json.append("\"id\":").append(rs.getInt("id"));
                json.append(",\"entityName\":").append(JsonUtil.quote(rs.getString("entity_name")));
                json.append(",\"entityType\":").append(JsonUtil.quote(rs.getString("entity_type")));
                json.append(",\"severity\":").append(JsonUtil.quote(rs.getString("severity")));
                json.append(",\"message\":").append(JsonUtil.quote(rs.getString("message")));
                json.append(",\"reportedBy\":").append(JsonUtil.quote(rs.getString("reported_by")));
                json.append(",\"reportedAt\":").append(JsonUtil.quote(rs.getString("reported_at")));
                json.append("}");
            }

        } catch (SQLException e) {
            log.error("Error listing active CloudGuard incidents: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
            return;
        }

        json.append("]");
        resp.getWriter().write(json.toString());
    }

    // ==================== handleServerStatus — GET /api/cloudguard/server-status ====================

    private void handleServerStatus(HttpServletResponse resp) throws IOException {
        String sql =
            "SELECT s.id, s.url, s.url_normalized, s.machine_name, s.current_version, " +
            "       c.company_name, " +
            "       shs.check_type, shs.last_status, shs.last_http_code, shs.last_error, " +
            "       shs.last_checked, shs.changed_at " +
            "FROM servers s " +
            "LEFT JOIN customers c ON c.id = s.customer_id " +
            "LEFT JOIN server_health_state shs ON shs.server_id = s.id " +
            "WHERE s.is_active = 1 " +
            "ORDER BY FIELD(shs.last_status, 'severe', 'medium', 'low', 'unknown', 'ok'), s.url_normalized";

        StringBuilder json = new StringBuilder("[");

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;

                json.append("{");
                json.append("\"id\":").append(rs.getInt("id"));
                json.append(",\"url\":").append(JsonUtil.quote(rs.getString("url")));
                json.append(",\"urlNormalized\":").append(JsonUtil.quote(rs.getString("url_normalized")));
                json.append(",\"machineName\":").append(JsonUtil.quote(rs.getString("machine_name")));
                json.append(",\"version\":").append(JsonUtil.quote(rs.getString("current_version")));
                json.append(",\"companyName\":").append(JsonUtil.quote(rs.getString("company_name")));
                json.append(",\"checkType\":").append(JsonUtil.quote(rs.getString("check_type")));
                json.append(",\"status\":").append(JsonUtil.quote(rs.getString("last_status")));
                json.append(",\"httpCode\":").append(rs.getInt("last_http_code"));
                json.append(",\"error\":").append(JsonUtil.quote(rs.getString("last_error")));
                json.append(",\"lastChecked\":").append(JsonUtil.quote(rs.getString("last_checked")));
                json.append(",\"changedAt\":").append(JsonUtil.quote(rs.getString("changed_at")));
                json.append("}");
            }

        } catch (SQLException e) {
            log.error("Error listing server health status: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
            return;
        }

        json.append("]");
        resp.getWriter().write(json.toString());
    }

    // ==================== handleHistory — GET /api/cloudguard/history ====================

    private void handleHistory(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int days = 30;
        int limit = 50;
        try {
            String daysParam = req.getParameter("days");
            if (daysParam != null) days = Math.max(1, Math.min(365, Integer.parseInt(daysParam)));
            String limitParam = req.getParameter("limit");
            if (limitParam != null) limit = Math.max(1, Math.min(500, Integer.parseInt(limitParam)));
        } catch (NumberFormatException ignored) {}

        String sql =
            "SELECT id, entity_name, entity_type, severity, message, " +
            "       reported_by, reported_at, resolved_at, resolved_by, resolution_note " +
            "FROM cloudguard " +
            "WHERE is_active = 0 AND resolved_at >= DATE_SUB(NOW(), INTERVAL ? DAY) " +
            "ORDER BY resolved_at DESC LIMIT ?";

        StringBuilder json = new StringBuilder("[");

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, days);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;

                    json.append("{");
                    json.append("\"id\":").append(rs.getInt("id"));
                    json.append(",\"entityName\":").append(JsonUtil.quote(rs.getString("entity_name")));
                    json.append(",\"entityType\":").append(JsonUtil.quote(rs.getString("entity_type")));
                    json.append(",\"severity\":").append(JsonUtil.quote(rs.getString("severity")));
                    json.append(",\"message\":").append(JsonUtil.quote(rs.getString("message")));
                    json.append(",\"reportedBy\":").append(JsonUtil.quote(rs.getString("reported_by")));
                    json.append(",\"reportedAt\":").append(JsonUtil.quote(rs.getString("reported_at")));
                    json.append(",\"resolvedAt\":").append(JsonUtil.quote(rs.getString("resolved_at")));
                    json.append(",\"resolvedBy\":").append(JsonUtil.quote(rs.getString("resolved_by")));
                    json.append(",\"resolutionNote\":").append(JsonUtil.quote(rs.getString("resolution_note")));
                    json.append("}");
                }
            }
        } catch (SQLException e) {
            log.error("Error listing CloudGuard history: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
            return;
        }

        json.append("]");
        resp.getWriter().write(json.toString());
    }

    // ==================== Helpers ====================

    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

}
