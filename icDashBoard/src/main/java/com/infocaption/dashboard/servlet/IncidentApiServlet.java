package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
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
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Incident / Change Management API Servlet.
 *
 * Endpoints:
 *   GET    /api/incidents              — List incidents (filterable, paginated)
 *   GET    /api/incidents/stats        — Summary statistics
 *   GET    /api/incidents/filters      — Unique filter values for dropdowns
 *   POST   /api/incidents              — Create incident
 *   PUT    /api/incidents              — Update incident (partial)
 *   DELETE /api/incidents?id=X         — Delete incident
 */
public class IncidentApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(IncidentApiServlet.class);

    private static final String[] VALID_LEVELS = {
        "Critical", "High", "Medium", "Low", "Change", "Security"
    };

    private static final String[] SORTABLE_COLUMNS = {
        "incident_time", "level", "server", "reporter_name", "created_at", "description"
    };

    // ==================== Init ====================

    @Override
    public void init() throws ServletException {
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `incidents` (" +
                "  `id`               INT AUTO_INCREMENT PRIMARY KEY," +
                "  `description`      TEXT NOT NULL," +
                "  `solution`         TEXT NULL," +
                "  `level`            ENUM('Critical','High','Medium','Low','Change','Security') NOT NULL DEFAULT 'Medium'," +
                "  `reporter_user_id` INT NULL," +
                "  `reporter_name`    VARCHAR(255) NULL," +
                "  `server`           VARCHAR(100) NULL," +
                "  `incident_time`    DATETIME NOT NULL," +
                "  `clear_time`       DATETIME NULL," +
                "  `created_by`       INT NULL," +
                "  `created_at`       TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  `updated_at`       TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  INDEX `idx_inc_level` (`level`)," +
                "  INDEX `idx_inc_server` (`server`)," +
                "  INDEX `idx_inc_time` (`incident_time`)," +
                "  INDEX `idx_inc_reporter` (`reporter_user_id`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            log.info("Incidents table verified/created successfully");
        } catch (SQLException e) {
            log.warn("Could not auto-create incidents table: {}", e.getMessage());
        }
    }

    // ==================== HTTP Method Handlers ====================

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = authenticate(req, resp);
        if (user == null) return;

        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        if (pathInfo.equals("/") || pathInfo.equals("")) {
            handleList(req, resp);
        } else if (pathInfo.equals("/stats")) {
            handleStats(resp);
        } else if (pathInfo.equals("/stats/monthly")) {
            handleMonthlyStats(resp);
        } else if (pathInfo.equals("/filters")) {
            handleFilters(resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = authenticate(req, resp);
        if (user == null) return;

        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        if (pathInfo.equals("/") || pathInfo.equals("")) {
            handleCreate(req, resp, user);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = authenticate(req, resp);
        if (user == null) return;

        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        if (pathInfo.equals("/") || pathInfo.equals("")) {
            handleUpdate(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = authenticate(req, resp);
        if (user == null) return;

        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        if (pathInfo.equals("/") || pathInfo.equals("")) {
            handleDelete(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    // ==================== List Handler ====================

    private void handleList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String levelFilter = req.getParameter("level");
        String serverFilter = req.getParameter("server");
        String reporterFilter = req.getParameter("reporter");
        String search = req.getParameter("search");
        String from = req.getParameter("from");
        String to = req.getParameter("to");
        String status = req.getParameter("status"); // open/closed
        int limit = parseIntParam(req, "limit", 200);
        int offset = parseIntParam(req, "offset", 0);
        String sort = req.getParameter("sort");
        String dir = req.getParameter("dir");

        if (!isValidSortColumn(sort)) sort = "incident_time";
        if (!"asc".equalsIgnoreCase(dir)) dir = "desc";

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");

        if (levelFilter != null && !levelFilter.isEmpty()) {
            String[] levels = levelFilter.split(",");
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < levels.length; i++) {
                if (isValidLevel(levels[i].trim())) {
                    if (placeholders.length() > 0) placeholders.append(",");
                    placeholders.append("?");
                    params.add(levels[i].trim());
                }
            }
            if (placeholders.length() > 0) {
                where.append(" AND i.level IN (").append(placeholders).append(")");
            }
        }

        if (serverFilter != null && !serverFilter.isEmpty()) {
            where.append(" AND i.server = ?");
            params.add(serverFilter);
        }

        if (reporterFilter != null && !reporterFilter.isEmpty()) {
            where.append(" AND (i.reporter_name = ? OR i.reporter_user_id = ?)");
            params.add(reporterFilter);
            try { params.add(Integer.parseInt(reporterFilter)); }
            catch (NumberFormatException e) { params.add(-1); }
        }

        if (search != null && !search.isEmpty()) {
            where.append(" AND (i.description LIKE ? OR i.solution LIKE ? OR i.reporter_name LIKE ? OR i.server LIKE ?)");
            String term = "%" + search + "%";
            params.add(term); params.add(term); params.add(term); params.add(term);
        }

        if (from != null && from.matches("\\d{4}-\\d{2}-\\d{2}")) {
            where.append(" AND i.incident_time >= ?");
            params.add(from + " 00:00:00");
        }

        if (to != null && to.matches("\\d{4}-\\d{2}-\\d{2}")) {
            where.append(" AND i.incident_time <= ?");
            params.add(to + " 23:59:59");
        }

        if ("open".equals(status)) {
            where.append(" AND i.clear_time IS NULL");
        } else if ("closed".equals(status)) {
            where.append(" AND i.clear_time IS NOT NULL");
        }

        String countSql = "SELECT COUNT(*) FROM incidents i" + where;
        String dataSql = "SELECT i.*, u.full_name AS reporter_full_name, c.full_name AS creator_full_name " +
                         "FROM incidents i " +
                         "LEFT JOIN users u ON i.reporter_user_id = u.id " +
                         "LEFT JOIN users c ON i.created_by = c.id" +
                         where + " ORDER BY i." + sort + " " + dir +
                         " LIMIT ? OFFSET ?";

        try (Connection conn = DBUtil.getConnection()) {
            int total = 0;
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                for (int i = 0; i < params.size(); i++) {
                    setParam(ps, i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) total = rs.getInt(1);
                }
            }

            StringBuilder json = new StringBuilder("{\"incidents\":[");
            try (PreparedStatement ps = conn.prepareStatement(dataSql)) {
                for (int i = 0; i < params.size(); i++) {
                    setParam(ps, i + 1, params.get(i));
                }
                ps.setInt(params.size() + 1, limit);
                ps.setInt(params.size() + 2, offset);
                try (ResultSet rs = ps.executeQuery()) {

                    boolean first = true;
                    while (rs.next()) {
                        if (!first) json.append(",");
                        first = false;
                        appendIncidentJson(json, rs);
                    }
                }
            }

            json.append("],\"total\":").append(total);
            json.append(",\"limit\":").append(limit);
            json.append(",\"offset\":").append(offset).append("}");
            resp.getWriter().write(json.toString());

        } catch (SQLException e) {
            log.error("Failed to list incidents", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Stats Handler ====================

    private void handleStats(HttpServletResponse resp) throws IOException {
        String sql = "SELECT " +
            "COUNT(*) AS total, " +
            "SUM(CASE WHEN clear_time IS NULL THEN 1 ELSE 0 END) AS open_count, " +
            "SUM(CASE WHEN level='Critical' THEN 1 ELSE 0 END) AS critical_count, " +
            "SUM(CASE WHEN level='High' THEN 1 ELSE 0 END) AS high_count, " +
            "SUM(CASE WHEN level='Medium' THEN 1 ELSE 0 END) AS medium_count, " +
            "SUM(CASE WHEN level='Low' THEN 1 ELSE 0 END) AS low_count, " +
            "SUM(CASE WHEN level='Change' THEN 1 ELSE 0 END) AS change_count, " +
            "SUM(CASE WHEN level='Security' THEN 1 ELSE 0 END) AS security_count, " +
            "SUM(CASE WHEN incident_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) THEN 1 ELSE 0 END) AS last30days, " +
            // Uptime: Critical+High in last 30 days, each = 1h downtime. 30d = 720h
            "SUM(CASE WHEN level IN ('Critical','High') AND incident_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) THEN 1 ELSE 0 END) AS downtime_30d, " +
            // Uptime: Critical+High in last 365 days, each = 1h downtime. 365d = 8760h
            "SUM(CASE WHEN level IN ('Critical','High') AND incident_time >= DATE_SUB(NOW(), INTERVAL 365 DAY) THEN 1 ELSE 0 END) AS downtime_365d " +
            "FROM incidents";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder();
            if (rs.next()) {
                int downtime30d = rs.getInt("downtime_30d");
                int downtime365d = rs.getInt("downtime_365d");
                double uptime30d = (720.0 - downtime30d) / 720.0 * 100.0;
                double uptime365d = (8760.0 - downtime365d) / 8760.0 * 100.0;

                json.append("{\"total\":").append(rs.getInt("total"));
                json.append(",\"open\":").append(rs.getInt("open_count"));
                json.append(",\"byLevel\":{");
                json.append("\"Critical\":").append(rs.getInt("critical_count"));
                json.append(",\"High\":").append(rs.getInt("high_count"));
                json.append(",\"Medium\":").append(rs.getInt("medium_count"));
                json.append(",\"Low\":").append(rs.getInt("low_count"));
                json.append(",\"Change\":").append(rs.getInt("change_count"));
                json.append(",\"Security\":").append(rs.getInt("security_count"));
                json.append("},\"last30days\":").append(rs.getInt("last30days"));
                json.append(",\"uptime30d\":").append(String.format(java.util.Locale.US, "%.4f", uptime30d));
                json.append(",\"uptime365d\":").append(String.format(java.util.Locale.US, "%.4f", uptime365d));
                json.append(",\"downtimeHours30d\":").append(downtime30d);
                json.append(",\"downtimeHours365d\":").append(downtime365d);
                json.append("}");
            } else {
                json.append("{\"total\":0,\"open\":0,\"byLevel\":{},\"last30days\":0,\"uptime30d\":100,\"uptime365d\":100,\"downtimeHours30d\":0,\"downtimeHours365d\":0}");
            }
            resp.getWriter().write(json.toString());

        } catch (SQLException e) {
            log.error("Failed to get incident stats", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Monthly Stats Handler ====================

    private void handleMonthlyStats(HttpServletResponse resp) throws IOException {
        String sql = "SELECT DATE_FORMAT(incident_time, '%Y-%m') AS month, " +
            "COUNT(*) AS total, " +
            "SUM(CASE WHEN level='Critical' THEN 1 ELSE 0 END) AS critical_count, " +
            "SUM(CASE WHEN level='High' THEN 1 ELSE 0 END) AS high_count, " +
            "SUM(CASE WHEN level='Medium' THEN 1 ELSE 0 END) AS medium_count, " +
            "SUM(CASE WHEN level='Low' THEN 1 ELSE 0 END) AS low_count, " +
            "SUM(CASE WHEN level='Change' THEN 1 ELSE 0 END) AS change_count, " +
            "SUM(CASE WHEN level='Security' THEN 1 ELSE 0 END) AS security_count " +
            "FROM incidents GROUP BY month ORDER BY month";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append("{\"month\":").append(JsonUtil.quote(rs.getString("month")));
                json.append(",\"total\":").append(rs.getInt("total"));
                json.append(",\"critical\":").append(rs.getInt("critical_count"));
                json.append(",\"high\":").append(rs.getInt("high_count"));
                json.append(",\"medium\":").append(rs.getInt("medium_count"));
                json.append(",\"low\":").append(rs.getInt("low_count"));
                json.append(",\"change\":").append(rs.getInt("change_count"));
                json.append(",\"security\":").append(rs.getInt("security_count"));
                json.append("}");
            }
            json.append("]");
            resp.getWriter().write(json.toString());

        } catch (SQLException e) {
            log.error("Failed to get monthly stats", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Filters Handler ====================

    private void handleFilters(HttpServletResponse resp) throws IOException {
        try (Connection conn = DBUtil.getConnection()) {
            StringBuilder json = new StringBuilder("{");

            // Unique servers
            json.append("\"servers\":[");
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT DISTINCT server FROM incidents WHERE server IS NOT NULL ORDER BY server")) {
                try (ResultSet rs = ps.executeQuery()) {
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) json.append(",");
                        first = false;
                        json.append(JsonUtil.quote(rs.getString("server")));
                    }
                }
            }
            json.append("],");

            // Unique reporters
            json.append("\"reporters\":[");
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT DISTINCT COALESCE(u.full_name, i.reporter_name) AS name, i.reporter_user_id " +
                    "FROM incidents i LEFT JOIN users u ON i.reporter_user_id = u.id " +
                    "WHERE i.reporter_name IS NOT NULL OR i.reporter_user_id IS NOT NULL " +
                    "ORDER BY name")) {
                try (ResultSet rs = ps.executeQuery()) {
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) json.append(",");
                        first = false;
                        json.append("{\"name\":").append(JsonUtil.quote(rs.getString("name")));
                        int uid = rs.getInt("reporter_user_id");
                        json.append(",\"userId\":").append(rs.wasNull() ? "null" : String.valueOf(uid));
                        json.append("}");
                    }
                }
            }
            json.append("],");

            // Levels (static)
            json.append("\"levels\":[\"Critical\",\"High\",\"Medium\",\"Low\",\"Change\",\"Security\"]");
            json.append("}");

            resp.getWriter().write(json.toString());

        } catch (SQLException e) {
            log.error("Failed to get incident filters", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Create Handler ====================

    private void handleCreate(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        String body = readRequestBody(req);
        String description = JsonUtil.extractJsonString(body, "description");
        if (description == null || description.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"description is required\"}");
            return;
        }

        String level = JsonUtil.extractJsonString(body, "level");
        if (!isValidLevel(level)) level = "Medium";

        String server = JsonUtil.extractJsonString(body, "server");
        String solution = JsonUtil.extractJsonString(body, "solution");
        String incidentTime = JsonUtil.extractJsonString(body, "incidentTime");
        String clearTime = JsonUtil.extractJsonString(body, "clearTime");

        if (incidentTime == null || incidentTime.isEmpty()) {
            incidentTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
        }

        String sql = "INSERT INTO incidents (description, solution, level, reporter_user_id, reporter_name, " +
                     "server, incident_time, clear_time, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, description.trim());
            ps.setString(2, solution);
            ps.setString(3, level);
            ps.setInt(4, user.getId());
            ps.setString(5, user.getFullName());
            ps.setString(6, server);
            ps.setString(7, incidentTime);
            if (clearTime != null && !clearTime.isEmpty()) {
                ps.setString(8, clearTime);
            } else {
                ps.setNull(8, Types.TIMESTAMP);
            }
            ps.setInt(9, user.getId());
            ps.executeUpdate();

            int id = 0;
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) id = rs.getInt(1);
            }

            resp.getWriter().write("{\"id\":" + id + "}");
        } catch (SQLException e) {
            log.error("Failed to create incident", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Update Handler ====================

    private void handleUpdate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readRequestBody(req);
        int id = JsonUtil.extractJsonInt(body, "id");
        if (id <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"id is required\"}");
            return;
        }

        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        addStringUpdate(body, "description", "description", setClauses, params);
        addStringUpdate(body, "solution", "solution", setClauses, params);
        addStringUpdate(body, "server", "server", setClauses, params);
        addStringUpdate(body, "incidentTime", "incident_time", setClauses, params);

        String level = JsonUtil.extractJsonString(body, "level");
        if (level != null && isValidLevel(level)) {
            setClauses.add("level = ?");
            params.add(level);
        }

        String clearTime = JsonUtil.extractJsonString(body, "clearTime");
        if (clearTime != null) {
            if (clearTime.isEmpty()) {
                setClauses.add("clear_time = NULL");
            } else {
                setClauses.add("clear_time = ?");
                params.add(clearTime);
            }
        }

        if (setClauses.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"No updates provided\"}");
            return;
        }

        String sql = "UPDATE incidents SET " + String.join(", ", setClauses) + " WHERE id = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object p : params) {
                if (p instanceof String) ps.setString(idx++, (String) p);
                else if (p instanceof Integer) ps.setInt(idx++, (Integer) p);
            }
            ps.setInt(idx, id);
            int rows = ps.executeUpdate();
            resp.getWriter().write("{\"updated\":" + rows + "}");
        } catch (SQLException e) {
            log.error("Failed to update incident {}", id, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Delete Handler ====================

    private void handleDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String idStr = req.getParameter("id");
        if (idStr == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"id parameter required\"}");
            return;
        }

        int id;
        try { id = Integer.parseInt(idStr); } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid id\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM incidents WHERE id = ?")) {
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            resp.getWriter().write("{\"deleted\":" + rows + "}");
        } catch (SQLException e) {
            log.error("Failed to delete incident {}", id, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Helpers ====================

    private void appendIncidentJson(StringBuilder json, ResultSet rs) throws SQLException {
        json.append("{");
        json.append("\"id\":").append(rs.getInt("id"));
        json.append(",\"description\":").append(JsonUtil.quote(rs.getString("description")));
        String sol = rs.getString("solution");
        json.append(",\"solution\":").append(sol != null ? JsonUtil.quote(sol) : "null");
        json.append(",\"level\":").append(JsonUtil.quote(rs.getString("level")));

        int reporterUid = rs.getInt("reporter_user_id");
        json.append(",\"reporterUserId\":").append(rs.wasNull() ? "null" : String.valueOf(reporterUid));
        json.append(",\"reporterName\":").append(JsonUtil.quote(rs.getString("reporter_name")));

        String reporterFull = rs.getString("reporter_full_name");
        json.append(",\"reporterFullName\":").append(reporterFull != null ? JsonUtil.quote(reporterFull) : "null");

        json.append(",\"server\":").append(JsonUtil.quote(rs.getString("server")));
        json.append(",\"incidentTime\":").append(JsonUtil.quote(rs.getString("incident_time")));

        String clearTime = rs.getString("clear_time");
        json.append(",\"clearTime\":").append(clearTime != null ? JsonUtil.quote(clearTime) : "null");

        int createdBy = rs.getInt("created_by");
        json.append(",\"createdBy\":").append(rs.wasNull() ? "null" : String.valueOf(createdBy));

        String creatorName = rs.getString("creator_full_name");
        json.append(",\"creatorFullName\":").append(creatorName != null ? JsonUtil.quote(creatorName) : "null");

        json.append(",\"createdAt\":").append(JsonUtil.quote(rs.getString("created_at")));
        json.append("}");
    }

    private void setParam(PreparedStatement ps, int idx, Object value) throws SQLException {
        if (value instanceof String) ps.setString(idx, (String) value);
        else if (value instanceof Integer) ps.setInt(idx, (Integer) value);
    }

    private void addStringUpdate(String body, String jsonKey, String dbColumn,
                                 List<String> setClauses, List<Object> params) {
        String value = JsonUtil.extractJsonString(body, jsonKey);
        if (value != null) {
            setClauses.add(dbColumn + " = ?");
            params.add(value);
        }
    }

    private boolean isValidLevel(String level) {
        if (level == null) return false;
        for (String l : VALID_LEVELS) {
            if (l.equals(level)) return true;
        }
        return false;
    }

    private boolean isValidSortColumn(String col) {
        if (col == null) return false;
        for (String c : SORTABLE_COLUMNS) {
            if (c.equals(col)) return true;
        }
        return false;
    }

    private int parseIntParam(HttpServletRequest req, String name, int defaultVal) {
        String val = req.getParameter(name);
        if (val == null) return defaultVal;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return defaultVal; }
    }

    // ==================== Auth & JSON Utilities ====================

    private User authenticate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write("{\"error\":\"Not authenticated\"}");
            return null;
        }
        return (User) session.getAttribute("user");
    }

    private String readRequestBody(HttpServletRequest req) throws IOException {
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
