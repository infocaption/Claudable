package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.AdminUtil;
import com.infocaption.dashboard.util.AppConfig;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.JsonUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tomcat Instance API Servlet — manage local Tomcat installations.
 *
 * GET  /api/tomcat-instances              — List all instances (with meta + health summary)
 * GET  /api/tomcat-instances/health-summary — Aggregated health summary
 * GET  /api/tomcat-instances/{id}/scan    — Scan: read server.xml + list deployed apps
 * GET  /api/tomcat-instances/{id}/health  — Health check all apps (saves to DB)
 *
 * POST   /api/tomcat-instances            — Add a new instance (name, installPath, meta)
 * PUT    /api/tomcat-instances/{id}       — Update instance meta-info
 * DELETE /api/tomcat-instances/{id}       — Remove an instance
 *
 * Background: Health checks all active instances every N minutes.
 * All endpoints require admin.
 */
public class TomcatInstanceApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(TomcatInstanceApiServlet.class);

    private static final int FALLBACK_HEALTH_INTERVAL = 5;
    private static final int FALLBACK_HEALTH_TIMEOUT = 5;

    private ScheduledExecutorService scheduler;
    private java.net.http.HttpClient httpClient;

    @Override
    public void init() throws ServletException {
        // Ensure tables exist
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS tomcat_instances (" +
                "  id           INT AUTO_INCREMENT PRIMARY KEY," +
                "  name         VARCHAR(255) NOT NULL," +
                "  install_path VARCHAR(500) NOT NULL," +
                "  is_active    TINYINT(1) DEFAULT 1," +
                "  last_scan_at TIMESTAMP NULL," +
                "  created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  UNIQUE INDEX idx_ti_path (install_path(255))" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            // Idempotent: add meta columns if missing
            safeAddColumn(stmt, "tomcat_instances", "machine_name", "VARCHAR(255) NULL AFTER name");
            safeAddColumn(stmt, "tomcat_instances", "environment",
                "ENUM('production','staging','test','development') DEFAULT 'production' AFTER machine_name");
            safeAddColumn(stmt, "tomcat_instances", "description", "VARCHAR(500) NULL AFTER environment");
            safeAddColumn(stmt, "tomcat_instances", "last_health_at", "TIMESTAMP NULL AFTER last_scan_at");
            safeAddColumn(stmt, "tomcat_instances", "health_ok", "INT DEFAULT 0 AFTER last_health_at");
            safeAddColumn(stmt, "tomcat_instances", "health_warn", "INT DEFAULT 0 AFTER health_ok");
            safeAddColumn(stmt, "tomcat_instances", "health_error", "INT DEFAULT 0 AFTER health_warn");
            safeAddColumn(stmt, "tomcat_instances", "http_port_override", "INT NULL AFTER health_error");
            safeAddColumn(stmt, "tomcat_instances", "https_port_override", "INT NULL AFTER http_port_override");
            safeAddColumn(stmt, "tomcat_instances", "is_ignored", "TINYINT(1) DEFAULT 0 AFTER https_port_override");

            // Scan data tables
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS tomcat_connectors (" +
                "  id           INT AUTO_INCREMENT PRIMARY KEY," +
                "  instance_id  INT NOT NULL," +
                "  port         INT NOT NULL," +
                "  is_ssl       TINYINT(1) DEFAULT 0," +
                "  FOREIGN KEY (instance_id) REFERENCES tomcat_instances(id) ON DELETE CASCADE," +
                "  INDEX idx_tc_inst (instance_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS tomcat_scan_hosts (" +
                "  id           INT AUTO_INCREMENT PRIMARY KEY," +
                "  instance_id  INT NOT NULL," +
                "  hostname     VARCHAR(255) NOT NULL," +
                "  app_base     VARCHAR(500) NULL," +
                "  FOREIGN KEY (instance_id) REFERENCES tomcat_instances(id) ON DELETE CASCADE," +
                "  INDEX idx_tsh_inst (instance_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            safeAddColumn(stmt, "tomcat_scan_hosts", "is_ignored", "TINYINT(1) DEFAULT 0 AFTER app_base");

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS tomcat_scan_host_aliases (" +
                "  id        INT AUTO_INCREMENT PRIMARY KEY," +
                "  host_id   INT NOT NULL," +
                "  alias     VARCHAR(255) NOT NULL," +
                "  FOREIGN KEY (host_id) REFERENCES tomcat_scan_hosts(id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS tomcat_scan_host_contexts (" +
                "  id        INT AUTO_INCREMENT PRIMARY KEY," +
                "  host_id   INT NOT NULL," +
                "  path      VARCHAR(255) DEFAULT '/'," +
                "  doc_base  VARCHAR(500) NULL," +
                "  FOREIGN KEY (host_id) REFERENCES tomcat_scan_hosts(id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS tomcat_apps (" +
                "  id            INT AUTO_INCREMENT PRIMARY KEY," +
                "  instance_id   INT NOT NULL," +
                "  name          VARCHAR(255) NOT NULL," +
                "  context_path  VARCHAR(255) NOT NULL," +
                "  has_web_inf   TINYINT(1) DEFAULT 0," +
                "  version       VARCHAR(100) NULL," +
                "  FOREIGN KEY (instance_id) REFERENCES tomcat_instances(id) ON DELETE CASCADE," +
                "  INDEX idx_ta_inst (instance_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS tomcat_users (" +
                "  id           INT AUTO_INCREMENT PRIMARY KEY," +
                "  instance_id  INT NOT NULL," +
                "  username     VARCHAR(255) NOT NULL," +
                "  roles        VARCHAR(500) NULL," +
                "  FOREIGN KEY (instance_id) REFERENCES tomcat_instances(id) ON DELETE CASCADE," +
                "  INDEX idx_tu_inst (instance_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS tomcat_health_results (" +
                "  id           INT AUTO_INCREMENT PRIMARY KEY," +
                "  instance_id  INT NOT NULL," +
                "  target_name  VARCHAR(255) NOT NULL," +
                "  target_host  VARCHAR(255) NOT NULL," +
                "  target_url   VARCHAR(500) NOT NULL," +
                "  context_path VARCHAR(255) DEFAULT '/'," +
                "  doc_base     VARCHAR(500) NULL," +
                "  status       ENUM('ok','warning','error') DEFAULT 'error'," +
                "  status_code  INT DEFAULT -1," +
                "  response_time_ms INT DEFAULT -1," +
                "  error_message VARCHAR(500) NULL," +
                "  checked_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  FOREIGN KEY (instance_id) REFERENCES tomcat_instances(id) ON DELETE CASCADE," +
                "  INDEX idx_thr_instance (instance_id)," +
                "  INDEX idx_thr_checked (checked_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            safeAddColumn(stmt, "tomcat_health_results", "response_time_ms", "INT DEFAULT -1 AFTER status_code");
        } catch (SQLException e) {
            log.warn("Could not ensure tomcat tables: {}", e.getMessage());
        }

        // Create reusable HttpClient with permissive SSL for local checks
        httpClient = createPermissiveHttpClient();

        // Start background health check scheduler
        int interval = AppConfig.getInt("tomcat.healthInterval", FALLBACK_HEALTH_INTERVAL);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TomcatInstance-HealthCheck");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                runBackgroundHealthChecks();
            } catch (Exception e) {
                log.error("Background health check error: {}", e.getMessage());
            }
        }, 1, interval, TimeUnit.MINUTES);

        log.info("TomcatInstanceApiServlet initialized — health check every {} min", interval);
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            log.info("TomcatInstance health check scheduler stopped");
        }
    }

    private void safeAddColumn(Statement stmt, String table, String column, String definition) {
        try {
            stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) { // 1060 = Duplicate column
                log.warn("Could not add column {}.{}: {}", table, column, e.getMessage());
            }
        }
    }

    // ==================== HTTP Handlers ====================

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        if (pathInfo.equals("/") || pathInfo.isEmpty()) {
            handleList(resp);
        } else if (pathInfo.equals("/health-summary")) {
            handleHealthSummary(resp);
        } else if (pathInfo.matches("/\\d+/scan/?")) {
            int id = extractId(pathInfo);
            handleScan(id, resp);
        } else if (pathInfo.matches("/\\d+/health/?")) {
            int id = extractId(pathInfo);
            handleHealth(id, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        resp.setContentType("application/json; charset=UTF-8");
        String body = readBody(req);
        handleAdd(body, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        resp.setContentType("application/json; charset=UTF-8");
        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.matches("/\\d+/?")) {
            int id = extractId(pathInfo);
            String body = readBody(req);
            handleUpdate(id, body, resp);
        } else if (pathInfo != null && pathInfo.matches("/\\d+/ignore/?")) {
            int id = extractId(pathInfo);
            handleToggleInstanceIgnore(id, resp);
        } else if (pathInfo != null && pathInfo.matches("/host/\\d+/ignore/?")) {
            int hostId = extractId(pathInfo.substring("/host".length()));
            handleToggleHostIgnore(hostId, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        resp.setContentType("application/json; charset=UTF-8");
        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.matches("/\\d+/?")) {
            int id = extractId(pathInfo);
            handleDelete(id, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    // ==================== List ====================

    private void handleList(HttpServletResponse resp) throws IOException {
        String sql = "SELECT ti.* " +
            "FROM tomcat_instances ti WHERE ti.is_active = 1 ORDER BY ti.name";

        try (Connection conn = DBUtil.getConnection()) {
            // Phase 1: Collect all instance base data (close RS before nested queries)
            List<Object[]> instanceRows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    String machineName = rs.getString("machine_name");
                    String environment = rs.getString("environment");
                    String description = rs.getString("description");
                    String installPath = rs.getString("install_path");
                    String lastScanAt = rs.getString("last_scan_at");
                    String lastHealthAt = rs.getString("last_health_at");
                    int healthOk = rs.getInt("health_ok");
                    int healthWarn = rs.getInt("health_warn");
                    int healthError = rs.getInt("health_error");
                    int httpOverride = rs.getInt("http_port_override");
                    boolean httpNull = rs.wasNull();
                    int httpsOverride = rs.getInt("https_port_override");
                    boolean httpsNull = rs.wasNull();
                    int isIgnored = 0;
                    try { isIgnored = rs.getInt("is_ignored"); } catch (SQLException ignored) {}
                    instanceRows.add(new Object[]{
                        id, name, machineName, environment, description, installPath,
                        lastScanAt, lastHealthAt, healthOk, healthWarn, healthError,
                        httpOverride, httpNull, httpsOverride, httpsNull, isIgnored
                    });
                }
            }

            // Phase 2: Build JSON with nested queries (main RS is closed)
            // Pre-check all paths in parallel with timeout (avoids hanging on unreachable network paths)
            ExecutorService pathChecker = Executors.newFixedThreadPool(
                Math.min(instanceRows.size(), 4), r -> { Thread t = new Thread(r, "PathCheck"); t.setDaemon(true); return t; });
            boolean[] pathResults = new boolean[instanceRows.size()];
            List<Future<Boolean>> pathFutures = new ArrayList<>();
            for (Object[] row : instanceRows) {
                final String ip = (String) row[5];
                pathFutures.add(pathChecker.submit(() -> Paths.get(ip).toFile().isDirectory()));
            }
            for (int i = 0; i < pathFutures.size(); i++) {
                try { pathResults[i] = pathFutures.get(i).get(3, TimeUnit.SECONDS); }
                catch (Exception ignored) { pathResults[i] = false; }
            }
            pathChecker.shutdownNow();

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < instanceRows.size(); i++) {
                if (i > 0) json.append(",");
                Object[] row = instanceRows.get(i);
                int instId = (int) row[0];
                String installPath = (String) row[5];

                json.append("{");
                json.append("\"id\":").append(instId);
                json.append(",\"name\":").append(JsonUtil.quote((String) row[1]));
                json.append(",\"machineName\":").append(JsonUtil.quote((String) row[2]));
                json.append(",\"environment\":").append(JsonUtil.quote((String) row[3]));
                json.append(",\"description\":").append(JsonUtil.quote((String) row[4]));
                json.append(",\"installPath\":").append(JsonUtil.quote(installPath));
                json.append(",\"lastScanAt\":").append(JsonUtil.quote((String) row[6]));
                json.append(",\"lastHealthAt\":").append(JsonUtil.quote((String) row[7]));
                json.append(",\"healthOk\":").append((int) row[8]);
                json.append(",\"healthWarn\":").append((int) row[9]);
                json.append(",\"healthError\":").append((int) row[10]);
                json.append(",\"pathExists\":").append(pathResults[i]);
                json.append(",\"ignored\":").append((int) row[15] == 1);
                if (!(boolean) row[12]) json.append(",\"httpPortOverride\":").append((int) row[11]);
                if (!(boolean) row[14]) json.append(",\"httpsPortOverride\":").append((int) row[13]);

                // Append scan/health data — each wrapped so one failure doesn't break the list
                json.append(",\"healthResults\":");
                try { appendHealthResults(conn, instId, json); } catch (SQLException e) {
                    log.warn("Error loading health results for instance {}: {}", instId, e.getMessage());
                    json.append("[]");
                }
                json.append(",\"connectors\":");
                try { appendConnectors(conn, instId, json); } catch (SQLException e) {
                    log.warn("Error loading connectors for instance {}: {}", instId, e.getMessage());
                    json.append("[]");
                }
                json.append(",\"hosts\":");
                try { appendHosts(conn, instId, json); } catch (SQLException e) {
                    log.warn("Error loading hosts for instance {}: {}", instId, e.getMessage());
                    json.append("[]");
                }
                json.append(",\"apps\":");
                try { appendApps(conn, instId, json); } catch (SQLException e) {
                    log.warn("Error loading apps for instance {}: {}", instId, e.getMessage());
                    json.append("[]");
                }
                json.append(",\"users\":");
                try { appendUsers(conn, instId, json); } catch (SQLException e) {
                    log.warn("Error loading users for instance {}: {}", instId, e.getMessage());
                    json.append("[]");
                }

                json.append("}");
            }
            json.append("]");
            resp.getWriter().write(json.toString());

        } catch (SQLException e) {
            log.error("Error listing instances: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void appendHealthResults(Connection conn, int instanceId, StringBuilder json) throws SQLException {
        String sql = "SELECT * FROM tomcat_health_results WHERE instance_id = ? ORDER BY target_name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                json.append("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{");
                    json.append("\"name\":").append(JsonUtil.quote(rs.getString("target_name")));
                    json.append(",\"host\":").append(JsonUtil.quote(rs.getString("target_host")));
                    json.append(",\"url\":").append(JsonUtil.quote(rs.getString("target_url")));
                    json.append(",\"contextPath\":").append(JsonUtil.quote(rs.getString("context_path")));
                    String docBase = rs.getString("doc_base");
                    if (docBase != null) json.append(",\"docBase\":").append(JsonUtil.quote(docBase));
                    json.append(",\"status\":").append(JsonUtil.quote(rs.getString("status")));
                    json.append(",\"statusCode\":").append(rs.getInt("status_code"));
                    try {
                        int rtMs = rs.getInt("response_time_ms");
                        if (!rs.wasNull() && rtMs >= 0) json.append(",\"responseTimeMs\":").append(rtMs);
                    } catch (SQLException ignored) { /* column may not exist yet */ }
                    String err = rs.getString("error_message");
                    if (err != null) json.append(",\"error\":").append(JsonUtil.quote(err));
                    json.append(",\"checkedAt\":").append(JsonUtil.quote(rs.getString("checked_at")));
                    json.append("}");
                }
                json.append("]");
            }
        }
    }

    private void appendConnectors(Connection conn, int instanceId, StringBuilder json) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT port, is_ssl FROM tomcat_connectors WHERE instance_id = ? ORDER BY port")) {
            ps.setInt(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                json.append("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{\"port\":").append(rs.getInt("port"));
                    json.append(",\"ssl\":").append(rs.getInt("is_ssl") == 1).append("}");
                }
                json.append("]");
            }
        }
    }

    private void appendHosts(Connection conn, int instanceId, StringBuilder json) throws SQLException {
        // Single flat query with LEFT JOINs to avoid nested ResultSets
        // Also join servers + customers to get company info by matching hostname against url_normalized
        String sql = "SELECT h.id AS host_id, h.hostname, h.app_base, h.is_ignored, " +
            "a.alias, ctx.path AS ctx_path, ctx.doc_base AS ctx_doc_base, " +
            "s.url AS server_url, cust.company_name, cust.company_id AS so_company_id " +
            "FROM tomcat_scan_hosts h " +
            "LEFT JOIN tomcat_scan_host_aliases a ON a.host_id = h.id " +
            "LEFT JOIN tomcat_scan_host_contexts ctx ON ctx.host_id = h.id " +
            "LEFT JOIN servers s ON s.url_normalized = h.hostname AND s.is_active = 1 " +
            "LEFT JOIN customers cust ON cust.id = s.customer_id " +
            "WHERE h.instance_id = ? " +
            "ORDER BY h.hostname, a.alias, ctx.path";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                // hostId -> [hostname, appBase, isIgnored, serverUrl, companyName, soCompanyId]
                java.util.LinkedHashMap<Integer, String[]> hostMap = new java.util.LinkedHashMap<>();
                java.util.Map<Integer, java.util.LinkedHashSet<String>> aliasMap = new java.util.HashMap<>();
                java.util.Map<Integer, java.util.LinkedHashSet<String>> ctxMap = new java.util.HashMap<>();

                while (rs.next()) {
                    int hid = rs.getInt("host_id");
                    if (!hostMap.containsKey(hid)) {
                        hostMap.put(hid, new String[]{
                            rs.getString("hostname"),
                            rs.getString("app_base"),
                            String.valueOf(rs.getInt("is_ignored")),
                            rs.getString("server_url"),
                            rs.getString("company_name"),
                            rs.getString("so_company_id")
                        });
                        aliasMap.put(hid, new java.util.LinkedHashSet<>());
                        ctxMap.put(hid, new java.util.LinkedHashSet<>());
                    }
                    String alias = rs.getString("alias");
                    if (alias != null) aliasMap.get(hid).add(alias);
                    String ctxPath = rs.getString("ctx_path");
                    String ctxDocBase = rs.getString("ctx_doc_base");
                    if (ctxPath != null) ctxMap.get(hid).add(ctxPath + "\0" + (ctxDocBase != null ? ctxDocBase : ""));
                }

                // Build JSON
                json.append("[");
                boolean first = true;
                for (java.util.Map.Entry<Integer, String[]> entry : hostMap.entrySet()) {
                    if (!first) json.append(",");
                    first = false;
                    int hid = entry.getKey();
                    String[] data = entry.getValue();
                    json.append("{\"id\":").append(hid);
                    json.append(",\"name\":").append(JsonUtil.quote(data[0]));
                    json.append(",\"appBase\":").append(JsonUtil.quote(data[1]));
                    json.append(",\"ignored\":").append("1".equals(data[2]));
                    if (data[3] != null) json.append(",\"serverUrl\":").append(JsonUtil.quote(data[3]));
                    if (data[4] != null) json.append(",\"companyName\":").append(JsonUtil.quote(data[4]));
                    if (data[5] != null) json.append(",\"companyId\":").append(JsonUtil.quote(data[5]));

                    json.append(",\"aliases\":[");
                    boolean af = true;
                    for (String a : aliasMap.get(hid)) {
                        if (!af) json.append(",");
                        af = false;
                        json.append(JsonUtil.quote(a));
                    }
                    json.append("]");

                    json.append(",\"contexts\":[");
                    boolean cf = true;
                    for (String c : ctxMap.get(hid)) {
                        if (!cf) json.append(",");
                        cf = false;
                        String[] parts = c.split("\0", -1);
                        json.append("{\"path\":").append(JsonUtil.quote(parts[0]));
                        json.append(",\"docBase\":").append(JsonUtil.quote(parts.length > 1 ? parts[1] : "")).append("}");
                    }
                    json.append("]");

                    json.append("}");
                }
                json.append("]");
            }
        }
    }

    private void appendApps(Connection conn, int instanceId, StringBuilder json) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name, context_path, has_web_inf, version FROM tomcat_apps WHERE instance_id = ? ORDER BY name")) {
            ps.setInt(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                json.append("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{\"name\":").append(JsonUtil.quote(rs.getString("name")));
                    json.append(",\"contextPath\":").append(JsonUtil.quote(rs.getString("context_path")));
                    json.append(",\"hasWebInf\":").append(rs.getInt("has_web_inf") == 1);
                    String ver = rs.getString("version");
                    if (ver != null) json.append(",\"version\":").append(JsonUtil.quote(ver));
                    json.append("}");
                }
                json.append("]");
            }
        }
    }

    private void appendUsers(Connection conn, int instanceId, StringBuilder json) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT username, roles FROM tomcat_users WHERE instance_id = ? ORDER BY username")) {
            ps.setInt(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                json.append("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{\"username\":").append(JsonUtil.quote(rs.getString("username")));
                    json.append(",\"roles\":").append(JsonUtil.quote(rs.getString("roles") != null ? rs.getString("roles") : ""));
                    json.append("}");
                }
                json.append("]");
            }
        }
    }

    // ==================== Toggle Ignore ====================

    private void handleToggleInstanceIgnore(int id, HttpServletResponse resp) throws IOException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE tomcat_instances SET is_ignored = NOT is_ignored WHERE id = ?")) {
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                resp.getWriter().write("{\"success\":true}");
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"Instance not found\"}");
            }
        } catch (SQLException e) {
            log.error("Error toggling instance ignore: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleToggleHostIgnore(int hostId, HttpServletResponse resp) throws IOException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE tomcat_scan_hosts SET is_ignored = NOT is_ignored WHERE id = ?")) {
            ps.setInt(1, hostId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                // Recalculate instance health summary excluding ignored hosts
                recalcHealthSummary(conn, hostId);
                resp.getWriter().write("{\"success\":true}");
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"Host not found\"}");
            }
        } catch (SQLException e) {
            log.error("Error toggling host ignore: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    /** Recalculate health_ok/warn/error on the instance, excluding ignored scan hosts. */
    private void recalcHealthSummary(Connection conn, int scanHostId) {
        try {
            // Find instance_id for this scan host
            int instanceId = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT instance_id FROM tomcat_scan_hosts WHERE id = ?")) {
                ps.setInt(1, scanHostId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) instanceId = rs.getInt("instance_id");
                }
            }
            if (instanceId == 0) return;

            // Get ignored hostnames for this instance
            java.util.Set<String> ignoredHosts = getIgnoredHostnames(conn, instanceId);

            // Recount from health_results excluding ignored
            int ok = 0, warn = 0, err = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT target_host, status FROM tomcat_health_results WHERE instance_id = ?")) {
                ps.setInt(1, instanceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (ignoredHosts.contains(rs.getString("target_host"))) continue;
                        String st = rs.getString("status");
                        if ("ok".equals(st)) ok++;
                        else if ("warning".equals(st)) warn++;
                        else err++;
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE tomcat_instances SET health_ok = ?, health_warn = ?, health_error = ? WHERE id = ?")) {
                ps.setInt(1, ok);
                ps.setInt(2, warn);
                ps.setInt(3, err);
                ps.setInt(4, instanceId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            log.warn("Error recalculating health summary: {}", e.getMessage());
        }
    }

    /** Returns set of hostnames that are marked as ignored for an instance. */
    private java.util.Set<String> getIgnoredHostnames(Connection conn, int instanceId) throws SQLException {
        java.util.Set<String> ignored = new java.util.HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT hostname FROM tomcat_scan_hosts WHERE instance_id = ? AND is_ignored = 1")) {
            ps.setInt(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ignored.add(rs.getString("hostname"));
            }
        }
        return ignored;
    }

    // ==================== Health Summary ====================

    private void handleHealthSummary(HttpServletResponse resp) throws IOException {
        String sql = "SELECT COUNT(*) AS instances, " +
            "SUM(health_ok) AS total_ok, SUM(health_warn) AS total_warn, SUM(health_error) AS total_error " +
            "FROM tomcat_instances WHERE is_active = 1";

        // Also count total apps from health results
        String appsSql = "SELECT COUNT(*) AS total_apps FROM tomcat_health_results thr " +
            "JOIN tomcat_instances ti ON thr.instance_id = ti.id WHERE ti.is_active = 1";

        try (Connection conn = DBUtil.getConnection()) {
            int instances = 0, totalOk = 0, totalWarn = 0, totalError = 0, totalApps = 0;

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    instances = rs.getInt("instances");
                    totalOk = rs.getInt("total_ok");
                    totalWarn = rs.getInt("total_warn");
                    totalError = rs.getInt("total_error");
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(appsSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) totalApps = rs.getInt("total_apps");
            }

            StringBuilder json = new StringBuilder("{");
            json.append("\"instances\":").append(instances);
            json.append(",\"totalApps\":").append(totalApps);
            json.append(",\"totalOk\":").append(totalOk);
            json.append(",\"totalWarn\":").append(totalWarn);
            json.append(",\"totalError\":").append(totalError);
            json.append("}");
            resp.getWriter().write(json.toString());

        } catch (SQLException e) {
            log.error("Error getting health summary: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Add ====================

    private void handleAdd(String body, HttpServletResponse resp) throws IOException {
        String name = JsonUtil.extractJsonString(body, "name");
        String installPath = JsonUtil.extractJsonString(body, "installPath");
        String machineName = JsonUtil.extractJsonString(body, "machineName");
        String environment = JsonUtil.extractJsonString(body, "environment");
        String description = JsonUtil.extractJsonString(body, "description");
        int httpPortOverride = JsonUtil.extractJsonInt(body, "httpPortOverride");
        int httpsPortOverride = JsonUtil.extractJsonInt(body, "httpsPortOverride");

        if (name == null || name.isEmpty() || installPath == null || installPath.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"name and installPath are required\"}");
            return;
        }

        // Validate path exists
        Path p = Paths.get(installPath);
        if (!p.toFile().isDirectory()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Path does not exist: " + installPath + "\"}");
            return;
        }

        // Check server.xml exists
        Path serverXml = p.resolve("conf").resolve("server.xml");
        if (!serverXml.toFile().exists()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"No conf/server.xml found at path\"}");
            return;
        }

        // Validate environment
        if (environment != null && !environment.isEmpty()) {
            if (!environment.matches("production|staging|test|development")) {
                environment = "production";
            }
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO tomcat_instances (name, install_path, machine_name, environment, description, http_port_override, https_port_override) VALUES (?, ?, ?, ?, ?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, installPath);
            ps.setString(3, machineName);
            ps.setString(4, environment != null && !environment.isEmpty() ? environment : "production");
            ps.setString(5, description);
            if (httpPortOverride > 0) ps.setInt(6, httpPortOverride); else ps.setNull(6, Types.INTEGER);
            if (httpsPortOverride > 0) ps.setInt(7, httpsPortOverride); else ps.setNull(7, Types.INTEGER);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                int newId = keys.next() ? keys.getInt(1) : 0;
                resp.getWriter().write("{\"success\":true,\"id\":" + newId + "}");
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                resp.setStatus(HttpServletResponse.SC_CONFLICT);
                resp.getWriter().write("{\"error\":\"This path is already registered\"}");
            } else {
                log.error("Error adding instance: {}", e.getMessage());
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\":\"Database error\"}");
            }
        }
    }

    // ==================== Update (PUT) ====================

    private void handleUpdate(int id, String body, HttpServletResponse resp) throws IOException {
        String name = JsonUtil.extractJsonString(body, "name");
        String machineName = JsonUtil.extractJsonString(body, "machineName");
        String environment = JsonUtil.extractJsonString(body, "environment");
        String description = JsonUtil.extractJsonString(body, "description");
        // Port overrides: 0 means "clear override", >0 means set, absent means don't change
        // We use extractJsonString to detect presence (null = absent)
        String httpPortStr = JsonUtil.extractJsonString(body, "httpPortOverride");
        String httpsPortStr = JsonUtil.extractJsonString(body, "httpsPortOverride");
        int httpPortOverride = JsonUtil.extractJsonInt(body, "httpPortOverride");
        int httpsPortOverride = JsonUtil.extractJsonInt(body, "httpsPortOverride");

        // Build dynamic UPDATE
        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (name != null) { setClauses.add("name = ?"); params.add(name); }
        if (machineName != null) { setClauses.add("machine_name = ?"); params.add(machineName); }
        if (environment != null) {
            if (environment.matches("production|staging|test|development")) {
                setClauses.add("environment = ?");
                params.add(environment);
            }
        }
        if (description != null) { setClauses.add("description = ?"); params.add(description); }
        // Port overrides are sent as integers in JSON; 0 clears the override
        if (body.contains("\"httpPortOverride\"")) {
            setClauses.add("http_port_override = ?");
            params.add(httpPortOverride > 0 ? httpPortOverride : null);
        }
        if (body.contains("\"httpsPortOverride\"")) {
            setClauses.add("https_port_override = ?");
            params.add(httpsPortOverride > 0 ? httpsPortOverride : null);
        }

        if (setClauses.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"No fields to update\"}");
            return;
        }

        String sql = "UPDATE tomcat_instances SET " + String.join(", ", setClauses) + " WHERE id = ? AND is_active = 1";
        params.add(id);

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                Object val = params.get(i);
                if (val == null) ps.setNull(i + 1, Types.INTEGER);
                else if (val instanceof Integer) ps.setInt(i + 1, (Integer) val);
                else ps.setString(i + 1, (String) val);
            }
            int rows = ps.executeUpdate();
            resp.getWriter().write(rows > 0 ? "{\"success\":true}" : "{\"error\":\"Not found\"}");
        } catch (SQLException e) {
            log.error("Error updating instance: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Delete ====================

    private void handleDelete(int id, HttpServletResponse resp) throws IOException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE tomcat_instances SET is_active = 0 WHERE id = ?")) {
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            resp.getWriter().write(rows > 0 ? "{\"success\":true}" : "{\"error\":\"Not found\"}");
        } catch (SQLException e) {
            log.error("Error deleting instance: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Scan ====================

    /**
     * Read server.xml + tomcat-users.xml, scan webapps directory.
     * Persists all scan data to DB, then returns the result as JSON.
     */
    private void handleScan(int id, HttpServletResponse resp) throws IOException {
        String installPath = getInstallPath(id);
        if (installPath == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Instance not found\"}");
            return;
        }

        Path base = Paths.get(installPath);
        Pattern attrPat = Pattern.compile("(\\w+)=\"([^\"]+)\"");

        // --- Parse data structures from filesystem ---
        List<int[]> connectors = new ArrayList<>();        // [port, ssl(0/1)]
        List<Object[]> hosts = new ArrayList<>();          // [name, appBase, aliases(List<String>), contexts(List<String[]>)]
        List<Object[]> apps = new ArrayList<>();           // [name, ctxPath, hasWebInf(0/1), version]
        List<String[]> users = new ArrayList<>();          // [username, roles]

        // --- server.xml ---
        Path serverXml = base.resolve("conf").resolve("server.xml");
        if (serverXml.toFile().exists()) {
            try {
                String content = new String(Files.readAllBytes(serverXml), "UTF-8");
                String clean = content.replaceAll("<!--[\\s\\S]*?-->", "");

                // Connectors
                Pattern connPat = Pattern.compile("<Connector\\s+[^>]*port=\"(\\d+)\"[^>]*/?>", Pattern.DOTALL);
                Matcher connMat = connPat.matcher(clean);
                while (connMat.find()) {
                    int port = Integer.parseInt(connMat.group(1));
                    String block = clean.substring(connMat.start(), connMat.end());
                    boolean ssl = block.contains("SSLEnabled=\"true\"");
                    connectors.add(new int[]{port, ssl ? 1 : 0});
                }

                // Hosts
                Pattern hostBlockPat = Pattern.compile("<Host\\s+([^>]*)>(.*?)</Host>", Pattern.DOTALL);
                Matcher hostMat = hostBlockPat.matcher(clean);
                while (hostMat.find()) {
                    String hostAttrs = hostMat.group(1);
                    String hostBody = hostMat.group(2);
                    String hostName = "", appBase = "";
                    Matcher am = attrPat.matcher(hostAttrs);
                    while (am.find()) {
                        if ("name".equals(am.group(1))) hostName = am.group(2);
                        if ("appBase".equals(am.group(1))) appBase = am.group(2);
                    }

                    List<String> aliases = new ArrayList<>();
                    Pattern aliasPat = Pattern.compile("<Alias>([^<]+)</Alias>");
                    Matcher aliasMat = aliasPat.matcher(hostBody);
                    while (aliasMat.find()) {
                        aliases.add(aliasMat.group(1).trim());
                    }

                    List<String[]> contexts = new ArrayList<>();
                    Pattern ctxPat = Pattern.compile("<Context\\s+([^>]*)/?>", Pattern.DOTALL);
                    Matcher ctxMat = ctxPat.matcher(hostBody);
                    while (ctxMat.find()) {
                        String ctxAttrs = ctxMat.group(1);
                        String path = "", docBase = "";
                        Matcher cam = attrPat.matcher(ctxAttrs);
                        while (cam.find()) {
                            if ("path".equals(cam.group(1))) path = cam.group(2);
                            if ("docBase".equals(cam.group(1))) docBase = cam.group(2);
                        }
                        contexts.add(new String[]{path, docBase});
                    }

                    hosts.add(new Object[]{hostName, appBase, aliases, contexts});
                }
            } catch (Exception e) {
                log.warn("Error reading server.xml for instance {}: {}", id, e.getMessage());
            }
        }

        // --- Scan webapps ---
        Path webapps = base.resolve("webapps");
        if (webapps.toFile().isDirectory()) {
            File[] entries = webapps.toFile().listFiles();
            if (entries != null) {
                for (File entry : entries) {
                    if (entry.isDirectory() && !entry.getName().startsWith(".")) {
                        String appName = entry.getName();
                        String ctxPath = appName.equals("ROOT") ? "/" : "/" + appName;
                        boolean hasWebInf = new File(entry, "WEB-INF").isDirectory();
                        String version = null;

                        File versionFile = new File(entry, ".version.xml");
                        if (versionFile.exists()) {
                            try {
                                String vContent = new String(Files.readAllBytes(versionFile.toPath()), "UTF-8");
                                Pattern verPat = Pattern.compile("<version>([^<]+)</version>");
                                Matcher verMat = verPat.matcher(vContent);
                                if (verMat.find()) version = verMat.group(1).trim();
                            } catch (Exception e) { /* ignore */ }
                        }

                        apps.add(new Object[]{appName, ctxPath, hasWebInf ? 1 : 0, version});
                    }
                }
            }
        }

        // --- tomcat-users.xml ---
        Path usersXml = base.resolve("conf").resolve("tomcat-users.xml");
        if (usersXml.toFile().exists()) {
            try {
                String content = new String(Files.readAllBytes(usersXml), "UTF-8");
                String clean = content.replaceAll("<!--[\\s\\S]*?-->", "");
                Pattern userPat = Pattern.compile("<user\\s+([^>]*)/?>", Pattern.DOTALL);
                Matcher userMat = userPat.matcher(clean);
                while (userMat.find()) {
                    String attrs = userMat.group(1);
                    Matcher attrMat = attrPat.matcher(attrs);
                    String username = null, roles = null;
                    while (attrMat.find()) {
                        if ("username".equals(attrMat.group(1))) username = attrMat.group(2);
                        if ("roles".equals(attrMat.group(1))) roles = attrMat.group(2);
                    }
                    if (username != null) {
                        users.add(new String[]{username, roles != null ? roles : ""});
                    }
                }
            } catch (Exception e) {
                log.warn("Error parsing tomcat-users.xml: {}", e.getMessage());
            }
        }

        // --- Save to DB ---
        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Clear old scan data
                for (String table : new String[]{"tomcat_connectors", "tomcat_apps", "tomcat_users"}) {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE instance_id = ?")) {
                        ps.setInt(1, id);
                        ps.executeUpdate();
                    }
                }
                // Preserve is_ignored flags before deleting hosts
                java.util.Set<String> previouslyIgnored = getIgnoredHostnames(conn, id);

                // Hosts cascade deletes aliases + contexts
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM tomcat_scan_hosts WHERE instance_id = ?")) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }

                // Insert connectors
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO tomcat_connectors (instance_id, port, is_ssl) VALUES (?, ?, ?)")) {
                    for (int[] c : connectors) {
                        ps.setInt(1, id);
                        ps.setInt(2, c[0]);
                        ps.setInt(3, c[1]);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // Insert hosts with aliases + contexts
                try (PreparedStatement hostPs = conn.prepareStatement(
                        "INSERT INTO tomcat_scan_hosts (instance_id, hostname, app_base, is_ignored) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    for (Object[] h : hosts) {
                        String hostName = (String) h[0];
                        hostPs.setInt(1, id);
                        hostPs.setString(2, hostName);
                        hostPs.setString(3, (String) h[1]);
                        hostPs.setInt(4, previouslyIgnored.contains(hostName) ? 1 : 0);
                        hostPs.executeUpdate();

                        int hostId;
                        try (ResultSet keys = hostPs.getGeneratedKeys()) {
                            keys.next();
                            hostId = keys.getInt(1);
                        }

                        @SuppressWarnings("unchecked")
                        List<String> aliases = (List<String>) h[2];
                        if (!aliases.isEmpty()) {
                            try (PreparedStatement aliasPs = conn.prepareStatement(
                                    "INSERT INTO tomcat_scan_host_aliases (host_id, alias) VALUES (?, ?)")) {
                                for (String alias : aliases) {
                                    aliasPs.setInt(1, hostId);
                                    aliasPs.setString(2, alias);
                                    aliasPs.addBatch();
                                }
                                aliasPs.executeBatch();
                            }
                        }

                        @SuppressWarnings("unchecked")
                        List<String[]> contexts = (List<String[]>) h[3];
                        if (!contexts.isEmpty()) {
                            try (PreparedStatement ctxPs = conn.prepareStatement(
                                    "INSERT INTO tomcat_scan_host_contexts (host_id, path, doc_base) VALUES (?, ?, ?)")) {
                                for (String[] ctx : contexts) {
                                    ctxPs.setInt(1, hostId);
                                    ctxPs.setString(2, ctx[0]);
                                    ctxPs.setString(3, ctx[1]);
                                    ctxPs.addBatch();
                                }
                                ctxPs.executeBatch();
                            }
                        }
                    }
                }

                // Insert apps
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO tomcat_apps (instance_id, name, context_path, has_web_inf, version) VALUES (?, ?, ?, ?, ?)")) {
                    for (Object[] a : apps) {
                        ps.setInt(1, id);
                        ps.setString(2, (String) a[0]);
                        ps.setString(3, (String) a[1]);
                        ps.setInt(4, (int) a[2]);
                        ps.setString(5, (String) a[3]);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // Insert users
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO tomcat_users (instance_id, username, roles) VALUES (?, ?, ?)")) {
                    for (String[] u : users) {
                        ps.setInt(1, id);
                        ps.setString(2, u[0]);
                        ps.setString(3, u[1]);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // Update last_scan_at
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE tomcat_instances SET last_scan_at = NOW() WHERE id = ?")) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                log.error("Error saving scan data for instance {}: {}", id, e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Error connecting to DB for scan save: {}", e.getMessage());
        }

        // --- Build JSON response ---
        StringBuilder json = new StringBuilder("{");
        json.append("\"installPath\":").append(JsonUtil.quote(installPath));

        json.append(",\"connectors\":[");
        for (int i = 0; i < connectors.size(); i++) {
            if (i > 0) json.append(",");
            json.append("{\"port\":").append(connectors.get(i)[0]);
            json.append(",\"ssl\":").append(connectors.get(i)[1] == 1).append("}");
        }
        json.append("]");

        json.append(",\"hosts\":[");
        for (int i = 0; i < hosts.size(); i++) {
            if (i > 0) json.append(",");
            Object[] h = hosts.get(i);
            json.append("{\"name\":").append(JsonUtil.quote((String) h[0]));
            json.append(",\"appBase\":").append(JsonUtil.quote((String) h[1]));

            @SuppressWarnings("unchecked")
            List<String> aliases = (List<String>) h[2];
            json.append(",\"aliases\":[");
            for (int j = 0; j < aliases.size(); j++) {
                if (j > 0) json.append(",");
                json.append(JsonUtil.quote(aliases.get(j)));
            }
            json.append("]");

            @SuppressWarnings("unchecked")
            List<String[]> contexts = (List<String[]>) h[3];
            json.append(",\"contexts\":[");
            for (int j = 0; j < contexts.size(); j++) {
                if (j > 0) json.append(",");
                json.append("{\"path\":").append(JsonUtil.quote(contexts.get(j)[0]));
                json.append(",\"docBase\":").append(JsonUtil.quote(contexts.get(j)[1])).append("}");
            }
            json.append("]");

            json.append("}");
        }
        json.append("]");

        json.append(",\"apps\":[");
        for (int i = 0; i < apps.size(); i++) {
            if (i > 0) json.append(",");
            Object[] a = apps.get(i);
            json.append("{\"name\":").append(JsonUtil.quote((String) a[0]));
            json.append(",\"contextPath\":").append(JsonUtil.quote((String) a[1]));
            json.append(",\"hasWebInf\":").append((int) a[2] == 1);
            if (a[3] != null) json.append(",\"version\":").append(JsonUtil.quote((String) a[3]));
            json.append("}");
        }
        json.append("]");

        json.append(",\"users\":[");
        for (int i = 0; i < users.size(); i++) {
            if (i > 0) json.append(",");
            json.append("{\"username\":").append(JsonUtil.quote(users.get(i)[0]));
            json.append(",\"roles\":").append(JsonUtil.quote(users.get(i)[1])).append("}");
        }
        json.append("]");

        json.append("}");
        resp.getWriter().write(json.toString());
    }

    // ==================== Health Check ====================

    /**
     * HTTP GET each deployed app/virtual host to check if it responds.
     * Now also saves results to tomcat_health_results table.
     */
    private void handleHealth(int id, HttpServletResponse resp) throws IOException {
        int[] instanceData = getInstanceData(id);
        if (instanceData == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Instance not found\"}");
            return;
        }

        String installPath = getInstallPath(id);
        HealthCheckResult result = performHealthCheck(id, installPath, instanceData[0], instanceData[1]);

        // Build JSON response
        StringBuilder json = new StringBuilder("{\"httpPort\":").append(result.httpPort);
        if (result.httpsPort > 0) json.append(",\"httpsPort\":").append(result.httpsPort);
        json.append(",\"results\":[");
        boolean first = true;
        for (TargetResult tr : result.targets) {
            if (!first) json.append(",");
            first = false;
            json.append("{");
            json.append("\"name\":").append(JsonUtil.quote(tr.name));
            json.append(",\"host\":").append(JsonUtil.quote(tr.host));
            json.append(",\"contextPath\":").append(JsonUtil.quote(tr.contextPath));
            if (tr.docBase != null && !tr.docBase.isEmpty()) {
                json.append(",\"docBase\":").append(JsonUtil.quote(tr.docBase));
            }
            json.append(",\"url\":").append(JsonUtil.quote(tr.url));
            json.append(",\"status\":").append(JsonUtil.quote(tr.status));
            json.append(",\"statusCode\":").append(tr.statusCode);
            if (tr.responseTimeMs >= 0) json.append(",\"responseTimeMs\":").append(tr.responseTimeMs);
            if (tr.errorMessage != null) {
                json.append(",\"error\":").append(JsonUtil.quote(tr.errorMessage));
            }
            json.append("}");
        }
        json.append("]}");
        resp.getWriter().write(json.toString());
    }

    // ==================== Background Health Checks ====================

    private void runBackgroundHealthChecks() {
        log.debug("Running background health checks for all Tomcat instances");
        int perInstanceTimeout = AppConfig.getInt("tomcat.healthTimeout", FALLBACK_HEALTH_TIMEOUT);
        // Max time per instance: (timeout × 3 probes × targets) + overhead; cap at 2 min
        int maxSecondsPerInstance = Math.min(perInstanceTimeout * 3 * 30 + 10, 120);

        String sql = "SELECT id, install_path, http_port_override, https_port_override FROM tomcat_instances WHERE is_active = 1 AND (is_ignored = 0 OR is_ignored IS NULL)";

        // Collect instances first, then close ResultSet before running health checks
        List<int[]> instances = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int httpOvr = rs.getInt("http_port_override");
                if (rs.wasNull()) httpOvr = 0;
                int httpsOvr = rs.getInt("https_port_override");
                if (rs.wasNull()) httpsOvr = 0;
                instances.add(new int[]{rs.getInt("id"), httpOvr, httpsOvr});
                paths.add(rs.getString("install_path"));
            }
        } catch (SQLException e) {
            log.error("Error loading instances for health check: {}", e.getMessage());
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TomcatInstance-HealthWorker");
            t.setDaemon(true);
            return t;
        });

        for (int i = 0; i < instances.size(); i++) {
            final int[] inst = instances.get(i);
            final String installPath = paths.get(i);
            Future<?> future = executor.submit(() ->
                performHealthCheck(inst[0], installPath, inst[1], inst[2])
            );
            try {
                future.get(maxSecondsPerInstance, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                log.warn("Health check timed out for instance {} ({}s limit)", inst[0], maxSecondsPerInstance);
            } catch (Exception e) {
                log.warn("Health check failed for instance {}: {}", inst[0], e.getMessage());
            }
        }

        executor.shutdownNow();

        // Cleanup old results (> 24h)
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM tomcat_health_results WHERE checked_at < DATE_SUB(NOW(), INTERVAL 24 HOUR)")) {
            int deleted = ps.executeUpdate();
            if (deleted > 0) log.debug("Cleaned up {} old health results", deleted);
        } catch (SQLException e) {
            log.warn("Error cleaning old health results: {}", e.getMessage());
        }
    }

    /**
     * Performs health check for a single instance and saves results to DB.
     * @param httpPortOverride if > 0, overrides the HTTP port from server.xml (e.g. Apache24 fronting)
     * @param httpsPortOverride if > 0, overrides the HTTPS port from server.xml
     */
    private HealthCheckResult performHealthCheck(int instanceId, String installPath,
                                                  int httpPortOverride, int httpsPortOverride) {
        HealthCheckResult result = new HealthCheckResult();

        // Parse server.xml for connectors and hosts
        Path serverXml = Paths.get(installPath, "conf", "server.xml");
        result.httpPort = 8080;
        result.httpsPort = -1;
        List<String[]> targets = new ArrayList<>();

        if (serverXml.toFile().exists()) {
            try {
                String content = new String(Files.readAllBytes(serverXml), "UTF-8");
                String clean = content.replaceAll("<!--[\\s\\S]*?-->", "");

                // Find HTTP and HTTPS ports
                Pattern connPat = Pattern.compile("<Connector\\s+([^>]*)/?>", Pattern.DOTALL);
                Matcher connMat = connPat.matcher(clean);
                while (connMat.find()) {
                    String attrs = connMat.group(1);
                    String port = extractAttr(attrs, "port");
                    boolean ssl = attrs.contains("SSLEnabled=\"true\"");
                    if (port != null) {
                        if (ssl) {
                            result.httpsPort = Integer.parseInt(port);
                        } else if (result.httpPort == 8080) {
                            result.httpPort = Integer.parseInt(port);
                        }
                    }
                }

                // Parse hosts and their contexts
                Pattern hostBlockPat = Pattern.compile("<Host\\s+([^>]*)>(.*?)</Host>", Pattern.DOTALL);
                Matcher hostMat = hostBlockPat.matcher(clean);
                while (hostMat.find()) {
                    String hostAttrs = hostMat.group(1);
                    String hostBody = hostMat.group(2);
                    String hostName = extractAttr(hostAttrs, "name");
                    if (hostName == null) continue;

                    Pattern ctxPat = Pattern.compile("<Context\\s+([^>]*)/?>", Pattern.DOTALL);
                    Matcher ctxMat = ctxPat.matcher(hostBody);
                    boolean hasContext = false;
                    while (ctxMat.find()) {
                        hasContext = true;
                        String ctxAttrs = ctxMat.group(1);
                        String path = extractAttr(ctxAttrs, "path");
                        String docBase = extractAttr(ctxAttrs, "docBase");
                        if (path == null) path = "";
                        targets.add(new String[]{hostName, path, docBase != null ? docBase : "", hostName + path});
                    }

                    if (!hasContext && !"localhost".equals(hostName)) {
                        targets.add(new String[]{hostName, "/", "", hostName});
                    }
                }
            } catch (Exception e) {
                log.warn("Error parsing server.xml for health: {}", e.getMessage());
            }
        }

        // Apply port overrides (e.g. Apache24 fronting Tomcat)
        if (httpPortOverride > 0) result.httpPort = httpPortOverride;
        if (httpsPortOverride > 0) result.httpsPort = httpsPortOverride;

        // Also scan webapps/ for localhost apps
        Path webapps = Paths.get(installPath, "webapps");
        if (webapps.toFile().isDirectory()) {
            File[] entries = webapps.toFile().listFiles();
            if (entries != null) {
                for (File entry : entries) {
                    if (entry.isDirectory() && !entry.getName().startsWith(".") &&
                        new File(entry, "WEB-INF").isDirectory()) {
                        String name = entry.getName();
                        String ctx = name.equals("ROOT") ? "/" : "/" + name;
                        targets.add(new String[]{"localhost", ctx, "", name});
                    }
                }
            }
        }

        // Load ignored hostnames to exclude from summary counts
        java.util.Set<String> ignoredHosts = new java.util.HashSet<>();
        try (Connection conn = DBUtil.getConnection()) {
            ignoredHosts = getIgnoredHostnames(conn, instanceId);
        } catch (SQLException e) { /* proceed without ignore list */ }

        int timeout = AppConfig.getInt("tomcat.healthTimeout", FALLBACK_HEALTH_TIMEOUT);
        int okCount = 0, warnCount = 0, errorCount = 0;

        // Health check each target with probe fallback: .version.xml -> ping.txt -> /
        String[] probeSuffixes = {"/.version.xml", "/ping.txt", "/"};

        for (String[] target : targets) {
            String hostName = target[0];
            String ctxPath = target[1];
            String docBase = target[2];
            String label = target[3];

            boolean useSSL = result.httpsPort > 0 && !"localhost".equals(hostName);
            int port = useSSL ? result.httpsPort : result.httpPort;
            String scheme = useSSL ? "https" : "http";
            String baseUrl = scheme + "://" + hostName + ":" + port + ctxPath;
            if (!baseUrl.endsWith("/")) baseUrl += "/";
            // Remove trailing slash for clean suffix append
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

            TargetResult tr = new TargetResult();
            tr.name = label;
            tr.host = hostName;
            tr.contextPath = ctxPath;
            tr.docBase = docBase;

            // Try each probe in order, stop at first non-404
            for (String suffix : probeSuffixes) {
                String probeUrl = base + suffix;
                tr.url = probeUrl;
                try {
                    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(probeUrl))
                            .timeout(java.time.Duration.ofSeconds(timeout))
                            .GET()
                            .build();

                    long startMs = System.currentTimeMillis();
                    java.net.http.HttpResponse<Void> response = httpClient.send(request,
                            java.net.http.HttpResponse.BodyHandlers.discarding());
                    tr.responseTimeMs = System.currentTimeMillis() - startMs;

                    int code = response.statusCode();
                    tr.statusCode = code;
                    tr.errorMessage = null;

                    if (code >= 200 && code < 400) {
                        tr.status = "ok";
                        break; // Success, no need to try more probes
                    } else if (code == 404) {
                        tr.status = "warning";
                        // Try next probe
                    } else {
                        tr.status = "error";
                        break; // Non-404 error, stop probing
                    }
                } catch (Exception e) {
                    tr.status = "error";
                    tr.statusCode = -1;
                    tr.errorMessage = e.getMessage();
                    break; // Connection error, stop probing
                }
            }

            // Only count non-ignored hosts in summary
            if (!ignoredHosts.contains(hostName)) {
                if ("ok".equals(tr.status)) okCount++;
                else if ("warning".equals(tr.status)) warnCount++;
                else errorCount++;
            }

            result.targets.add(tr);
        }

        // Save results to database
        try (Connection conn = DBUtil.getConnection()) {
            // Delete old results for this instance
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM tomcat_health_results WHERE instance_id = ?")) {
                ps.setInt(1, instanceId);
                ps.executeUpdate();
            }

            // Insert new results (try with response_time_ms, fall back without)
            boolean hasRtCol = true;
            try {
                String insertSql = "INSERT INTO tomcat_health_results " +
                    "(instance_id, target_name, target_host, target_url, context_path, doc_base, status, status_code, response_time_ms, error_message) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (TargetResult tr : result.targets) {
                        ps.setInt(1, instanceId);
                        ps.setString(2, tr.name);
                        ps.setString(3, tr.host);
                        ps.setString(4, tr.url);
                        ps.setString(5, tr.contextPath);
                        ps.setString(6, tr.docBase != null && !tr.docBase.isEmpty() ? tr.docBase : null);
                        ps.setString(7, tr.status);
                        ps.setInt(8, tr.statusCode);
                        ps.setLong(9, tr.responseTimeMs);
                        ps.setString(10, tr.errorMessage);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            } catch (SQLException rtEx) {
                // Column may not exist yet — retry without response_time_ms
                hasRtCol = false;
                String insertSql = "INSERT INTO tomcat_health_results " +
                    "(instance_id, target_name, target_host, target_url, context_path, doc_base, status, status_code, error_message) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (TargetResult tr : result.targets) {
                        ps.setInt(1, instanceId);
                        ps.setString(2, tr.name);
                        ps.setString(3, tr.host);
                        ps.setString(4, tr.url);
                        ps.setString(5, tr.contextPath);
                        ps.setString(6, tr.docBase != null && !tr.docBase.isEmpty() ? tr.docBase : null);
                        ps.setString(7, tr.status);
                        ps.setInt(8, tr.statusCode);
                        ps.setString(9, tr.errorMessage);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            // Update summary on instance
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE tomcat_instances SET last_health_at = NOW(), health_ok = ?, health_warn = ?, health_error = ? WHERE id = ?")) {
                ps.setInt(1, okCount);
                ps.setInt(2, warnCount);
                ps.setInt(3, errorCount);
                ps.setInt(4, instanceId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            log.warn("Error saving health results for instance {}: {}", instanceId, e.getMessage());
        }

        return result;
    }

    // ==================== Helper Classes ====================

    private static class HealthCheckResult {
        int httpPort = 8080;
        int httpsPort = -1;
        List<TargetResult> targets = new ArrayList<>();
    }

    private static class TargetResult {
        String name, host, contextPath, docBase, url, status, errorMessage;
        int statusCode = -1;
        long responseTimeMs = -1;
    }

    // ==================== Helpers ====================

    private static String extractAttr(String attrs, String name) {
        Pattern p = Pattern.compile(name + "=\"([^\"]+)\"");
        Matcher m = p.matcher(attrs);
        return m.find() ? m.group(1) : null;
    }

    /** Returns [httpPortOverride, httpsPortOverride] or null if not found. */
    private int[] getInstanceData(int id) {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT http_port_override, https_port_override FROM tomcat_instances WHERE id = ? AND is_active = 1")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                int httpOvr = rs.getInt("http_port_override");
                if (rs.wasNull()) httpOvr = 0;
                int httpsOvr = rs.getInt("https_port_override");
                if (rs.wasNull()) httpsOvr = 0;
                return new int[]{httpOvr, httpsOvr};
            }
        } catch (SQLException e) {
            return null;
        }
    }

    private String getInstallPath(int id) {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT install_path FROM tomcat_instances WHERE id = ? AND is_active = 1")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("install_path") : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    private static int extractId(String pathInfo) {
        Matcher m = Pattern.compile("/(\\d+)").matcher(pathInfo);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private java.net.http.HttpClient createPermissiveHttpClient() {
        try {
            javax.net.ssl.SSLContext sslCtx = javax.net.ssl.SSLContext.getInstance("TLS");
            sslCtx.init(null, new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
            }}, new java.security.SecureRandom());

            return java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(
                        AppConfig.getInt("tomcat.healthTimeout", FALLBACK_HEALTH_TIMEOUT)))
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .sslContext(sslCtx)
                    .build();
        } catch (Exception e) {
            log.warn("Could not create permissive SSL context: {}", e.getMessage());
            return java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(
                        AppConfig.getInt("tomcat.healthTimeout", FALLBACK_HEALTH_TIMEOUT)))
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .build();
        }
    }
}
