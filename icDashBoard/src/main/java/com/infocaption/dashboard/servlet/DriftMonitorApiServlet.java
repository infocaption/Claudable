package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.AppConfig;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.JsonUtil;
import com.infocaption.dashboard.util.SecretsConfig;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Drift Monitor API Servlet — infrastructure inventory.
 *
 * GET  /api/drift/machines              — All machines with services + host counts + health summary
 * GET  /api/drift/machines/{id}/hosts   — Tomcat hosts for a specific machine (all its Tomcat services)
 * GET  /api/drift/summary               — Aggregate summary (total machines, services, hosts, issues)
 *
 * POST /api/drift/machines              — Upsert machine (by hostname)
 * POST /api/drift/services              — Upsert service (by machine hostname + service_name)
 * POST /api/drift/hosts                 — Upsert host (by hostname)
 * POST /api/drift/bulk                  — Bulk upsert: machines + services + hosts in one call
 *
 * Supports both session auth and API key auth (X-API-Key header) for script access.
 */
public class DriftMonitorApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(DriftMonitorApiServlet.class);

    // ==================== Init (idempotent table creation) ====================

    @Override
    public void init() throws ServletException {
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS machines (" +
                "  id          INT AUTO_INCREMENT PRIMARY KEY," +
                "  hostname    VARCHAR(255) NOT NULL," +
                "  ip_address  VARCHAR(50) NULL," +
                "  os_name     VARCHAR(100) NULL," +
                "  os_version  VARCHAR(100) NULL," +
                "  cpu         VARCHAR(200) NULL," +
                "  ram_gb      INT NULL," +
                "  disk_info   VARCHAR(500) NULL," +
                "  environment ENUM('production','staging','test','development') DEFAULT 'production'," +
                "  location    VARCHAR(100) NULL," +
                "  notes       TEXT NULL," +
                "  is_active   TINYINT(1) DEFAULT 1," +
                "  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  UNIQUE INDEX idx_machine_hostname (hostname)," +
                "  INDEX idx_machine_ip (ip_address)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS machine_services (" +
                "  id              INT AUTO_INCREMENT PRIMARY KEY," +
                "  machine_id      INT NOT NULL," +
                "  service_type    ENUM('tomcat','mysql','java','iis','other') NOT NULL," +
                "  service_name    VARCHAR(255) NOT NULL," +
                "  display_name    VARCHAR(255) NULL," +
                "  version         VARCHAR(100) NULL," +
                "  install_path    VARCHAR(500) NULL," +
                "  port            INT NULL," +
                "  ssl_port        INT NULL," +
                "  status          ENUM('running','stopped','disabled','unknown') DEFAULT 'unknown'," +
                "  startup_type    ENUM('automatic','manual','disabled','unknown') DEFAULT 'unknown'," +
                "  last_status_check TIMESTAMP NULL," +
                "  notes           TEXT NULL," +
                "  is_active       TINYINT(1) DEFAULT 1," +
                "  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  FOREIGN KEY (machine_id) REFERENCES machines(id) ON DELETE CASCADE," +
                "  INDEX idx_service_machine (machine_id)," +
                "  INDEX idx_service_type (service_type, status)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS tomcat_hosts (" +
                "  id              INT AUTO_INCREMENT PRIMARY KEY," +
                "  service_id      INT NOT NULL," +
                "  server_id       INT NULL," +
                "  customer_id     INT NULL," +
                "  hostname        VARCHAR(255) NOT NULL," +
                "  context_path    VARCHAR(100) DEFAULT '/InfoCaptionCore'," +
                "  app_version     VARCHAR(50) NULL," +
                "  db_name         VARCHAR(100) NULL," +
                "  db_service_id   INT NULL," +
                "  health_status   ENUM('ok','warning','error','unknown') DEFAULT 'unknown'," +
                "  health_message  VARCHAR(500) NULL," +
                "  health_checked_at TIMESTAMP NULL," +
                "  status          ENUM('active','inactive','maintenance','decommissioned') DEFAULT 'active'," +
                "  notes           TEXT NULL," +
                "  is_active       TINYINT(1) DEFAULT 1," +
                "  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  FOREIGN KEY (service_id) REFERENCES machine_services(id) ON DELETE CASCADE," +
                "  INDEX idx_host_service (service_id)," +
                "  INDEX idx_host_health (health_status)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            log.info("Drift monitor tables verified/created successfully");
        } catch (SQLException e) {
            log.warn("Could not auto-create drift monitor tables: {}", e.getMessage());
        }
    }

    // ==================== HTTP Handlers ====================

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = authenticate(req, resp);
        if (user == null) return;

        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        if (pathInfo.equals("/machines") || pathInfo.equals("/machines/")) {
            handleListMachines(resp);
        } else if (pathInfo.equals("/summary") || pathInfo.equals("/summary/")) {
            handleSummary(resp);
        } else if (pathInfo.matches("/machines/\\d+/hosts/?")) {
            // Extract machine ID from path
            String[] parts = pathInfo.split("/");
            int machineId = Integer.parseInt(parts[2]);
            handleListHosts(machineId, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    // ==================== List Machines ====================

    private void handleListMachines(HttpServletResponse resp) throws IOException {
        String sql =
            "SELECT m.id, m.hostname, m.ip_address, m.os_name, m.os_version, " +
            "       m.cpu, m.ram_gb, m.disk_info, m.environment, m.location, m.notes, " +
            "       m.is_active, m.updated_at " +
            "FROM machines m " +
            "WHERE m.is_active = 1 " +
            "ORDER BY m.environment = 'production' DESC, m.hostname";

        StringBuilder json = new StringBuilder("[");

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                if (json.length() > 1) json.append(",");
                int machineId = rs.getInt("id");

                json.append("{");
                json.append("\"id\":").append(machineId);
                json.append(",\"hostname\":").append(JsonUtil.quote(rs.getString("hostname")));
                json.append(",\"ipAddress\":").append(JsonUtil.quote(rs.getString("ip_address")));
                json.append(",\"osName\":").append(JsonUtil.quote(rs.getString("os_name")));
                json.append(",\"osVersion\":").append(JsonUtil.quote(rs.getString("os_version")));
                json.append(",\"cpu\":").append(JsonUtil.quote(rs.getString("cpu")));
                json.append(",\"ramGb\":").append(rs.getObject("ram_gb") != null ? rs.getInt("ram_gb") : "null");
                json.append(",\"diskInfo\":").append(JsonUtil.quote(rs.getString("disk_info")));
                json.append(",\"environment\":").append(JsonUtil.quote(rs.getString("environment")));
                json.append(",\"location\":").append(JsonUtil.quote(rs.getString("location")));
                json.append(",\"notes\":").append(JsonUtil.quote(rs.getString("notes")));
                json.append(",\"updatedAt\":").append(JsonUtil.quote(rs.getString("updated_at")));

                // Inline: services for this machine
                appendServices(conn, machineId, json);

                // Inline: host counts and health summary
                appendHostSummary(conn, machineId, json);

                json.append("}");
            }
        } catch (SQLException e) {
            log.error("Error listing machines: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
            return;
        }

        json.append("]");
        resp.getWriter().write(json.toString());
    }

    private void appendServices(Connection conn, int machineId, StringBuilder json) throws SQLException {
        String sql =
            "SELECT id, service_type, service_name, display_name, version, install_path, " +
            "       port, ssl_port, status, startup_type, last_status_check, notes " +
            "FROM machine_services " +
            "WHERE machine_id = ? AND is_active = 1 " +
            "ORDER BY FIELD(service_type, 'java', 'mysql', 'tomcat', 'iis', 'other'), service_name";

        json.append(",\"services\":[");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, machineId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{");
                    json.append("\"id\":").append(rs.getInt("id"));
                    json.append(",\"serviceType\":").append(JsonUtil.quote(rs.getString("service_type")));
                    json.append(",\"serviceName\":").append(JsonUtil.quote(rs.getString("service_name")));
                    json.append(",\"displayName\":").append(JsonUtil.quote(rs.getString("display_name")));
                    json.append(",\"version\":").append(JsonUtil.quote(rs.getString("version")));
                    json.append(",\"installPath\":").append(JsonUtil.quote(rs.getString("install_path")));
                    json.append(",\"port\":").append(rs.getObject("port") != null ? rs.getInt("port") : "null");
                    json.append(",\"sslPort\":").append(rs.getObject("ssl_port") != null ? rs.getInt("ssl_port") : "null");
                    json.append(",\"status\":").append(JsonUtil.quote(rs.getString("status")));
                    json.append(",\"startupType\":").append(JsonUtil.quote(rs.getString("startup_type")));
                    json.append(",\"lastStatusCheck\":").append(JsonUtil.quote(rs.getString("last_status_check")));
                    json.append(",\"notes\":").append(JsonUtil.quote(rs.getString("notes")));
                    json.append("}");
                }
            }
        }
        json.append("]");
    }

    private void appendHostSummary(Connection conn, int machineId, StringBuilder json) throws SQLException {
        // Count hosts and health breakdown across ALL Tomcat services on this machine
        String sql =
            "SELECT COUNT(*) AS total_hosts, " +
            "       SUM(CASE WHEN th.health_status = 'ok' THEN 1 ELSE 0 END) AS ok_count, " +
            "       SUM(CASE WHEN th.health_status = 'warning' THEN 1 ELSE 0 END) AS warning_count, " +
            "       SUM(CASE WHEN th.health_status = 'error' THEN 1 ELSE 0 END) AS error_count, " +
            "       SUM(CASE WHEN th.health_status = 'unknown' THEN 1 ELSE 0 END) AS unknown_count " +
            "FROM tomcat_hosts th " +
            "JOIN machine_services ms ON th.service_id = ms.id " +
            "WHERE ms.machine_id = ? AND th.is_active = 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, machineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total_hosts");
                    int ok = rs.getInt("ok_count");
                    int warning = rs.getInt("warning_count");
                    int error = rs.getInt("error_count");
                    int unknown = rs.getInt("unknown_count");

                    // Machine is OK only if ALL hosts are ok
                    String machineHealth = "ok";
                    if (error > 0) machineHealth = "error";
                    else if (warning > 0) machineHealth = "warning";
                    else if (unknown > 0 && ok == 0) machineHealth = "unknown";

                    // Also check if any service is stopped
                    boolean hasStoppedService = false;
                    try (PreparedStatement ps2 = conn.prepareStatement(
                            "SELECT COUNT(*) FROM machine_services WHERE machine_id = ? AND status = 'stopped' AND is_active = 1")) {
                        ps2.setInt(1, machineId);
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            if (rs2.next() && rs2.getInt(1) > 0) {
                                hasStoppedService = true;
                                machineHealth = "error";
                            }
                        }
                    }

                    json.append(",\"hostCount\":").append(total);
                    json.append(",\"hostsOk\":").append(ok);
                    json.append(",\"hostsWarning\":").append(warning);
                    json.append(",\"hostsError\":").append(error);
                    json.append(",\"hostsUnknown\":").append(unknown);
                    json.append(",\"machineHealth\":").append(JsonUtil.quote(machineHealth));
                    json.append(",\"hasStoppedService\":").append(hasStoppedService);
                } else {
                    json.append(",\"hostCount\":0,\"hostsOk\":0,\"hostsWarning\":0,\"hostsError\":0,\"hostsUnknown\":0");
                    json.append(",\"machineHealth\":\"unknown\",\"hasStoppedService\":false");
                }
            }
        }
    }

    // ==================== List Hosts for Machine ====================

    private void handleListHosts(int machineId, HttpServletResponse resp) throws IOException {
        String sql =
            "SELECT th.id, th.service_id, th.hostname, th.context_path, th.app_version, " +
            "       th.db_name, th.health_status, th.health_message, th.health_checked_at, " +
            "       th.status, th.notes, th.server_id, th.customer_id, " +
            "       ms.service_name AS tomcat_service, ms.version AS tomcat_version, " +
            "       c.company_name " +
            "FROM tomcat_hosts th " +
            "JOIN machine_services ms ON th.service_id = ms.id " +
            "LEFT JOIN customers c ON th.customer_id = c.id " +
            "WHERE ms.machine_id = ? AND th.is_active = 1 " +
            "ORDER BY ms.service_name, th.hostname";

        StringBuilder json = new StringBuilder("[");

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, machineId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (json.length() > 1) json.append(",");
                    json.append("{");
                    json.append("\"id\":").append(rs.getInt("id"));
                    json.append(",\"serviceId\":").append(rs.getInt("service_id"));
                    json.append(",\"hostname\":").append(JsonUtil.quote(rs.getString("hostname")));
                    json.append(",\"contextPath\":").append(JsonUtil.quote(rs.getString("context_path")));
                    json.append(",\"appVersion\":").append(JsonUtil.quote(rs.getString("app_version")));
                    json.append(",\"dbName\":").append(JsonUtil.quote(rs.getString("db_name")));
                    json.append(",\"healthStatus\":").append(JsonUtil.quote(rs.getString("health_status")));
                    json.append(",\"healthMessage\":").append(JsonUtil.quote(rs.getString("health_message")));
                    json.append(",\"healthCheckedAt\":").append(JsonUtil.quote(rs.getString("health_checked_at")));
                    json.append(",\"status\":").append(JsonUtil.quote(rs.getString("status")));
                    json.append(",\"notes\":").append(JsonUtil.quote(rs.getString("notes")));
                    json.append(",\"serverId\":").append(rs.getObject("server_id") != null ? rs.getInt("server_id") : "null");
                    json.append(",\"customerId\":").append(rs.getObject("customer_id") != null ? rs.getInt("customer_id") : "null");
                    json.append(",\"tomcatService\":").append(JsonUtil.quote(rs.getString("tomcat_service")));
                    json.append(",\"tomcatVersion\":").append(JsonUtil.quote(rs.getString("tomcat_version")));
                    json.append(",\"companyName\":").append(JsonUtil.quote(rs.getString("company_name")));
                    json.append("}");
                }
            }
        } catch (SQLException e) {
            log.error("Error listing hosts for machine {}: {}", machineId, e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
            return;
        }

        json.append("]");
        resp.getWriter().write(json.toString());
    }

    // ==================== Summary ====================

    private void handleSummary(HttpServletResponse resp) throws IOException {
        StringBuilder json = new StringBuilder("{");

        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {

            // Machine count
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM machines WHERE is_active = 1")) {
                json.append("\"totalMachines\":").append(rs.next() ? rs.getInt(1) : 0);
            }

            // Service counts by type
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT service_type, COUNT(*) AS cnt, " +
                    "SUM(CASE WHEN status='running' THEN 1 ELSE 0 END) AS running, " +
                    "SUM(CASE WHEN status='stopped' THEN 1 ELSE 0 END) AS stopped " +
                    "FROM machine_services WHERE is_active = 1 GROUP BY service_type")) {
                json.append(",\"services\":{");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append(JsonUtil.quote(rs.getString("service_type"))).append(":{");
                    json.append("\"total\":").append(rs.getInt("cnt"));
                    json.append(",\"running\":").append(rs.getInt("running"));
                    json.append(",\"stopped\":").append(rs.getInt("stopped"));
                    json.append("}");
                }
                json.append("}");
            }

            // Host counts
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) AS total, " +
                    "SUM(CASE WHEN health_status='ok' THEN 1 ELSE 0 END) AS ok_count, " +
                    "SUM(CASE WHEN health_status='warning' THEN 1 ELSE 0 END) AS warning_count, " +
                    "SUM(CASE WHEN health_status='error' THEN 1 ELSE 0 END) AS error_count, " +
                    "SUM(CASE WHEN health_status='unknown' THEN 1 ELSE 0 END) AS unknown_count " +
                    "FROM tomcat_hosts WHERE is_active = 1")) {
                if (rs.next()) {
                    json.append(",\"totalHosts\":").append(rs.getInt("total"));
                    json.append(",\"hostsOk\":").append(rs.getInt("ok_count"));
                    json.append(",\"hostsWarning\":").append(rs.getInt("warning_count"));
                    json.append(",\"hostsError\":").append(rs.getInt("error_count"));
                    json.append(",\"hostsUnknown\":").append(rs.getInt("unknown_count"));
                }
            }

            // Version distribution
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT app_version, COUNT(*) AS cnt FROM tomcat_hosts " +
                    "WHERE is_active = 1 AND app_version IS NOT NULL " +
                    "GROUP BY app_version ORDER BY app_version DESC")) {
                json.append(",\"versionDistribution\":[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{\"version\":").append(JsonUtil.quote(rs.getString("app_version")));
                    json.append(",\"count\":").append(rs.getInt("cnt")).append("}");
                }
                json.append("]");
            }

        } catch (SQLException e) {
            log.error("Error building summary: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
            return;
        }

        json.append("}");
        resp.getWriter().write(json.toString());
    }

    // ==================== POST — Upsert endpoints ====================

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!authenticateAny(req, resp)) return;

        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        String body = readRequestBody(req);

        if (pathInfo.equals("/machines") || pathInfo.equals("/machines/")) {
            handleUpsertMachine(body, resp);
        } else if (pathInfo.equals("/services") || pathInfo.equals("/services/")) {
            handleUpsertService(body, resp);
        } else if (pathInfo.equals("/hosts") || pathInfo.equals("/hosts/")) {
            handleUpsertHost(body, resp);
        } else if (pathInfo.equals("/bulk") || pathInfo.equals("/bulk/")) {
            handleBulkUpsert(body, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // PUT delegates to same upsert logic as POST
        doPost(req, resp);
    }

    // ==================== Upsert Machine ====================

    /**
     * Upsert a machine by hostname.
     * JSON: {"hostname":"IC-PROD-01", "ipAddress":"10.0.0.1", "osName":"Windows Server 2022", ...}
     */
    private void handleUpsertMachine(String body, HttpServletResponse resp) throws IOException {
        String hostname = JsonUtil.extractJsonString(body, "hostname");
        if (hostname == null || hostname.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"hostname is required\"}");
            return;
        }

        String ipAddress   = JsonUtil.extractJsonString(body, "ipAddress");
        String osName      = JsonUtil.extractJsonString(body, "osName");
        String osVersion   = JsonUtil.extractJsonString(body, "osVersion");
        String cpu         = JsonUtil.extractJsonString(body, "cpu");
        Integer ramGb      = extractJsonInt(body, "ramGb");
        String diskInfo    = JsonUtil.extractJsonString(body, "diskInfo");
        String environment = JsonUtil.extractJsonString(body, "environment");
        String location    = JsonUtil.extractJsonString(body, "location");
        String notes       = JsonUtil.extractJsonString(body, "notes");

        String sql =
            "INSERT INTO machines (hostname, ip_address, os_name, os_version, cpu, ram_gb, disk_info, " +
            "                      environment, location, notes, is_active) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1) " +
            "ON DUPLICATE KEY UPDATE " +
            "  ip_address  = COALESCE(VALUES(ip_address), ip_address), " +
            "  os_name     = COALESCE(VALUES(os_name), os_name), " +
            "  os_version  = COALESCE(VALUES(os_version), os_version), " +
            "  cpu         = COALESCE(VALUES(cpu), cpu), " +
            "  ram_gb      = COALESCE(VALUES(ram_gb), ram_gb), " +
            "  disk_info   = COALESCE(VALUES(disk_info), disk_info), " +
            "  environment = COALESCE(VALUES(environment), environment), " +
            "  location    = COALESCE(VALUES(location), location), " +
            "  notes       = COALESCE(VALUES(notes), notes), " +
            "  is_active   = 1";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, hostname);
            ps.setString(2, ipAddress);
            ps.setString(3, osName);
            ps.setString(4, osVersion);
            ps.setString(5, cpu);
            ps.setObject(6, ramGb);  // null-safe
            ps.setString(7, diskInfo);
            ps.setString(8, environment != null ? environment : "production");
            ps.setString(9, location);
            ps.setString(10, notes);

            int rows = ps.executeUpdate();

            // Get the ID (either inserted or existing)
            int id = 0;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    id = keys.getInt(1);
                }
            }
            // If ON DUPLICATE KEY UPDATE, generated keys may be 0 — look up by hostname
            if (id == 0) {
                try (PreparedStatement lookup = conn.prepareStatement(
                        "SELECT id FROM machines WHERE hostname = ?")) {
                    lookup.setString(1, hostname);
                    try (ResultSet rs = lookup.executeQuery()) {
                        if (rs.next()) id = rs.getInt(1);
                    }
                }
            }

            String action = rows == 1 ? "created" : "updated";
            resp.getWriter().write("{\"action\":\"" + action + "\",\"id\":" + id +
                    ",\"hostname\":" + JsonUtil.quote(hostname) + "}");

        } catch (SQLException e) {
            log.error("Error upserting machine {}: {}", hostname, e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    // ==================== Upsert Service ====================

    /**
     * Upsert a service by machine hostname + service_name.
     * JSON: {"machineHostname":"IC-PROD-01", "serviceType":"tomcat", "serviceName":"Tomcat9_A", ...}
     */
    private void handleUpsertService(String body, HttpServletResponse resp) throws IOException {
        String machineHostname = JsonUtil.extractJsonString(body, "machineHostname");
        String serviceName     = JsonUtil.extractJsonString(body, "serviceName");
        String serviceType     = JsonUtil.extractJsonString(body, "serviceType");

        if (machineHostname == null || machineHostname.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"machineHostname is required\"}");
            return;
        }
        if (serviceName == null || serviceName.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"serviceName is required\"}");
            return;
        }
        if (serviceType == null || serviceType.isEmpty()) {
            serviceType = "other";
        }

        String displayName  = JsonUtil.extractJsonString(body, "displayName");
        String version      = JsonUtil.extractJsonString(body, "version");
        String installPath  = JsonUtil.extractJsonString(body, "installPath");
        Integer port        = extractJsonInt(body, "port");
        Integer sslPort     = extractJsonInt(body, "sslPort");
        String status       = JsonUtil.extractJsonString(body, "status");
        String startupType  = JsonUtil.extractJsonString(body, "startupType");
        String notes        = JsonUtil.extractJsonString(body, "notes");

        try (Connection conn = DBUtil.getConnection()) {

            // Resolve machine_id from hostname (auto-create machine if not found)
            int machineId = resolveOrCreateMachine(conn, machineHostname);

            // Check if service already exists for this machine + service_name
            int existingId = 0;
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT id FROM machine_services WHERE machine_id = ? AND service_name = ?")) {
                check.setInt(1, machineId);
                check.setString(2, serviceName);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) existingId = rs.getInt(1);
                }
            }

            if (existingId > 0) {
                // UPDATE existing
                String sql =
                    "UPDATE machine_services SET " +
                    "  service_type = COALESCE(?, service_type), " +
                    "  display_name = COALESCE(?, display_name), " +
                    "  version      = COALESCE(?, version), " +
                    "  install_path = COALESCE(?, install_path), " +
                    "  port         = COALESCE(?, port), " +
                    "  ssl_port     = COALESCE(?, ssl_port), " +
                    "  status       = COALESCE(?, status), " +
                    "  startup_type = COALESCE(?, startup_type), " +
                    "  notes        = COALESCE(?, notes), " +
                    "  last_status_check = NOW(), " +
                    "  is_active = 1 " +
                    "WHERE id = ?";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, serviceType);
                    ps.setString(2, displayName);
                    ps.setString(3, version);
                    ps.setString(4, installPath);
                    ps.setObject(5, port);
                    ps.setObject(6, sslPort);
                    ps.setString(7, status);
                    ps.setString(8, startupType);
                    ps.setString(9, notes);
                    ps.setInt(10, existingId);
                    ps.executeUpdate();
                }

                resp.getWriter().write("{\"action\":\"updated\",\"id\":" + existingId +
                        ",\"machineId\":" + machineId +
                        ",\"serviceName\":" + JsonUtil.quote(serviceName) + "}");

            } else {
                // INSERT new
                String sql =
                    "INSERT INTO machine_services (machine_id, service_type, service_name, display_name, " +
                    "  version, install_path, port, ssl_port, status, startup_type, notes, last_status_check, is_active) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), 1)";

                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, machineId);
                    ps.setString(2, serviceType);
                    ps.setString(3, serviceName);
                    ps.setString(4, displayName);
                    ps.setString(5, version);
                    ps.setString(6, installPath);
                    ps.setObject(7, port);
                    ps.setObject(8, sslPort);
                    ps.setString(9, status != null ? status : "unknown");
                    ps.setString(10, startupType != null ? startupType : "unknown");
                    ps.setString(11, notes);
                    ps.executeUpdate();

                    int newId = 0;
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) newId = keys.getInt(1);
                    }
                    resp.getWriter().write("{\"action\":\"created\",\"id\":" + newId +
                            ",\"machineId\":" + machineId +
                            ",\"serviceName\":" + JsonUtil.quote(serviceName) + "}");
                }
            }
        } catch (SQLException e) {
            log.error("Error upserting service {} on {}: {}", serviceName, machineHostname, e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    // ==================== Upsert Host ====================

    /**
     * Upsert a tomcat host by hostname.
     * JSON: {"machineHostname":"IC-PROD-01", "serviceName":"Tomcat9_A", "hostname":"demo.infocaption.com", ...}
     */
    private void handleUpsertHost(String body, HttpServletResponse resp) throws IOException {
        String machineHostname = JsonUtil.extractJsonString(body, "machineHostname");
        String serviceName     = JsonUtil.extractJsonString(body, "serviceName");
        String hostname        = JsonUtil.extractJsonString(body, "hostname");

        if (hostname == null || hostname.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"hostname is required\"}");
            return;
        }
        if (machineHostname == null || machineHostname.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"machineHostname is required\"}");
            return;
        }
        if (serviceName == null || serviceName.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"serviceName is required (the Tomcat service this host belongs to)\"}");
            return;
        }

        String contextPath   = JsonUtil.extractJsonString(body, "contextPath");
        String appVersion    = JsonUtil.extractJsonString(body, "appVersion");
        String dbName        = JsonUtil.extractJsonString(body, "dbName");
        String healthStatus  = JsonUtil.extractJsonString(body, "healthStatus");
        String healthMessage = JsonUtil.extractJsonString(body, "healthMessage");
        String status        = JsonUtil.extractJsonString(body, "status");
        String notes         = JsonUtil.extractJsonString(body, "notes");

        try (Connection conn = DBUtil.getConnection()) {

            // Resolve machine → service chain
            int machineId = resolveOrCreateMachine(conn, machineHostname);
            int serviceId = resolveOrCreateService(conn, machineId, serviceName, "tomcat");

            // Check if host already exists by hostname
            int existingId = 0;
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT id FROM tomcat_hosts WHERE hostname = ?")) {
                check.setString(1, hostname);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) existingId = rs.getInt(1);
                }
            }

            if (existingId > 0) {
                // UPDATE existing
                String sql =
                    "UPDATE tomcat_hosts SET " +
                    "  service_id     = ?, " +
                    "  context_path   = COALESCE(?, context_path), " +
                    "  app_version    = COALESCE(?, app_version), " +
                    "  db_name        = COALESCE(?, db_name), " +
                    "  health_status  = COALESCE(?, health_status), " +
                    "  health_message = ?, " +
                    "  health_checked_at = NOW(), " +
                    "  status         = COALESCE(?, status), " +
                    "  notes          = COALESCE(?, notes), " +
                    "  is_active = 1 " +
                    "WHERE id = ?";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, serviceId);
                    ps.setString(2, contextPath);
                    ps.setString(3, appVersion);
                    ps.setString(4, dbName);
                    ps.setString(5, healthStatus);
                    ps.setString(6, healthMessage);  // health_message always replaced (null = clear)
                    ps.setString(7, status);
                    ps.setString(8, notes);
                    ps.setInt(9, existingId);
                    ps.executeUpdate();
                }

                resp.getWriter().write("{\"action\":\"updated\",\"id\":" + existingId +
                        ",\"serviceId\":" + serviceId +
                        ",\"hostname\":" + JsonUtil.quote(hostname) + "}");

            } else {
                // INSERT new
                String sql =
                    "INSERT INTO tomcat_hosts (service_id, hostname, context_path, app_version, db_name, " +
                    "  health_status, health_message, health_checked_at, status, notes, is_active) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), ?, ?, 1)";

                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, serviceId);
                    ps.setString(2, hostname);
                    ps.setString(3, contextPath != null ? contextPath : "/InfoCaptionCore");
                    ps.setString(4, appVersion);
                    ps.setString(5, dbName);
                    ps.setString(6, healthStatus != null ? healthStatus : "unknown");
                    ps.setString(7, healthMessage);
                    ps.setString(8, status != null ? status : "active");
                    ps.setString(9, notes);
                    ps.executeUpdate();

                    int newId = 0;
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) newId = keys.getInt(1);
                    }
                    resp.getWriter().write("{\"action\":\"created\",\"id\":" + newId +
                            ",\"serviceId\":" + serviceId +
                            ",\"hostname\":" + JsonUtil.quote(hostname) + "}");
                }
            }
        } catch (SQLException e) {
            log.error("Error upserting host {}: {}", hostname, e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    // ==================== Bulk Upsert ====================

    /**
     * Bulk upsert: machines + services + hosts in one call.
     * JSON: {
     *   "machines": [{"hostname":"IC-PROD-01", "ipAddress":"10.0.0.1", ...}],
     *   "services": [{"machineHostname":"IC-PROD-01", "serviceName":"Tomcat9_A", ...}],
     *   "hosts": [{"machineHostname":"IC-PROD-01", "serviceName":"Tomcat9_A", "hostname":"demo.infocaption.com", ...}]
     * }
     */
    private void handleBulkUpsert(String body, HttpServletResponse resp) throws IOException {
        int machinesCount = 0, servicesCount = 0, hostsCount = 0;
        int errors = 0;
        List<String> errorMessages = new ArrayList<>();

        try (Connection conn = DBUtil.getConnection()) {

            // Process machines
            List<String> machineObjects = extractJsonObjectArray(body, "machines");
            for (String machineJson : machineObjects) {
                try {
                    bulkUpsertMachine(conn, machineJson);
                    machinesCount++;
                } catch (Exception e) {
                    errors++;
                    errorMessages.add("machine: " + e.getMessage());
                }
            }

            // Process services
            List<String> serviceObjects = extractJsonObjectArray(body, "services");
            for (String serviceJson : serviceObjects) {
                try {
                    bulkUpsertService(conn, serviceJson);
                    servicesCount++;
                } catch (Exception e) {
                    errors++;
                    errorMessages.add("service: " + e.getMessage());
                }
            }

            // Process hosts
            List<String> hostObjects = extractJsonObjectArray(body, "hosts");
            for (String hostJson : hostObjects) {
                try {
                    bulkUpsertHost(conn, hostJson);
                    hostsCount++;
                } catch (Exception e) {
                    errors++;
                    errorMessages.add("host: " + e.getMessage());
                }
            }

        } catch (SQLException e) {
            log.error("Bulk upsert DB connection error: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database connection error\"}");
            return;
        }

        StringBuilder json = new StringBuilder("{");
        json.append("\"machines\":").append(machinesCount);
        json.append(",\"services\":").append(servicesCount);
        json.append(",\"hosts\":").append(hostsCount);
        json.append(",\"errors\":").append(errors);
        if (!errorMessages.isEmpty()) {
            json.append(",\"errorMessages\":[");
            for (int i = 0; i < errorMessages.size(); i++) {
                if (i > 0) json.append(",");
                json.append(JsonUtil.quote(errorMessages.get(i)));
            }
            json.append("]");
        }
        json.append("}");
        resp.getWriter().write(json.toString());
    }

    // ---- Bulk helper methods (operate on a shared connection, throw on error) ----

    private void bulkUpsertMachine(Connection conn, String json) throws SQLException {
        String hostname = JsonUtil.extractJsonString(json, "hostname");
        if (hostname == null || hostname.isEmpty()) throw new SQLException("hostname is required");

        String sql =
            "INSERT INTO machines (hostname, ip_address, os_name, os_version, cpu, ram_gb, disk_info, " +
            "                      environment, location, notes, is_active) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1) " +
            "ON DUPLICATE KEY UPDATE " +
            "  ip_address  = COALESCE(VALUES(ip_address), ip_address), " +
            "  os_name     = COALESCE(VALUES(os_name), os_name), " +
            "  os_version  = COALESCE(VALUES(os_version), os_version), " +
            "  cpu         = COALESCE(VALUES(cpu), cpu), " +
            "  ram_gb      = COALESCE(VALUES(ram_gb), ram_gb), " +
            "  disk_info   = COALESCE(VALUES(disk_info), disk_info), " +
            "  environment = COALESCE(VALUES(environment), environment), " +
            "  location    = COALESCE(VALUES(location), location), " +
            "  notes       = COALESCE(VALUES(notes), notes), " +
            "  is_active   = 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hostname);
            ps.setString(2, JsonUtil.extractJsonString(json, "ipAddress"));
            ps.setString(3, JsonUtil.extractJsonString(json, "osName"));
            ps.setString(4, JsonUtil.extractJsonString(json, "osVersion"));
            ps.setString(5, JsonUtil.extractJsonString(json, "cpu"));
            ps.setObject(6, extractJsonInt(json, "ramGb"));
            ps.setString(7, JsonUtil.extractJsonString(json, "diskInfo"));
            String env = JsonUtil.extractJsonString(json, "environment");
            ps.setString(8, env != null ? env : "production");
            ps.setString(9, JsonUtil.extractJsonString(json, "location"));
            ps.setString(10, JsonUtil.extractJsonString(json, "notes"));
            ps.executeUpdate();
        }
    }

    private void bulkUpsertService(Connection conn, String json) throws SQLException {
        String machineHostname = JsonUtil.extractJsonString(json, "machineHostname");
        String serviceName = JsonUtil.extractJsonString(json, "serviceName");
        String serviceType = JsonUtil.extractJsonString(json, "serviceType");
        if (machineHostname == null || machineHostname.isEmpty()) throw new SQLException("machineHostname is required");
        if (serviceName == null || serviceName.isEmpty()) throw new SQLException("serviceName is required");
        if (serviceType == null || serviceType.isEmpty()) serviceType = "other";

        int machineId = resolveOrCreateMachine(conn, machineHostname);

        // Check existing
        int existingId = 0;
        try (PreparedStatement check = conn.prepareStatement(
                "SELECT id FROM machine_services WHERE machine_id = ? AND service_name = ?")) {
            check.setInt(1, machineId);
            check.setString(2, serviceName);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) existingId = rs.getInt(1);
            }
        }

        if (existingId > 0) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE machine_services SET service_type=COALESCE(?,service_type), " +
                    "display_name=COALESCE(?,display_name), version=COALESCE(?,version), " +
                    "install_path=COALESCE(?,install_path), port=COALESCE(?,port), ssl_port=COALESCE(?,ssl_port), " +
                    "status=COALESCE(?,status), startup_type=COALESCE(?,startup_type), notes=COALESCE(?,notes), " +
                    "last_status_check=NOW(), is_active=1 WHERE id=?")) {
                ps.setString(1, serviceType);
                ps.setString(2, JsonUtil.extractJsonString(json, "displayName"));
                ps.setString(3, JsonUtil.extractJsonString(json, "version"));
                ps.setString(4, JsonUtil.extractJsonString(json, "installPath"));
                ps.setObject(5, extractJsonInt(json, "port"));
                ps.setObject(6, extractJsonInt(json, "sslPort"));
                ps.setString(7, JsonUtil.extractJsonString(json, "status"));
                ps.setString(8, JsonUtil.extractJsonString(json, "startupType"));
                ps.setString(9, JsonUtil.extractJsonString(json, "notes"));
                ps.setInt(10, existingId);
                ps.executeUpdate();
            }
        } else {
            String status = JsonUtil.extractJsonString(json, "status");
            String startupType = JsonUtil.extractJsonString(json, "startupType");
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO machine_services (machine_id, service_type, service_name, display_name, " +
                    "version, install_path, port, ssl_port, status, startup_type, notes, last_status_check, is_active) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), 1)")) {
                ps.setInt(1, machineId);
                ps.setString(2, serviceType);
                ps.setString(3, serviceName);
                ps.setString(4, JsonUtil.extractJsonString(json, "displayName"));
                ps.setString(5, JsonUtil.extractJsonString(json, "version"));
                ps.setString(6, JsonUtil.extractJsonString(json, "installPath"));
                ps.setObject(7, extractJsonInt(json, "port"));
                ps.setObject(8, extractJsonInt(json, "sslPort"));
                ps.setString(9, status != null ? status : "unknown");
                ps.setString(10, startupType != null ? startupType : "unknown");
                ps.setString(11, JsonUtil.extractJsonString(json, "notes"));
                ps.executeUpdate();
            }
        }
    }

    private void bulkUpsertHost(Connection conn, String json) throws SQLException {
        String machineHostname = JsonUtil.extractJsonString(json, "machineHostname");
        String serviceName = JsonUtil.extractJsonString(json, "serviceName");
        String hostname = JsonUtil.extractJsonString(json, "hostname");
        if (hostname == null || hostname.isEmpty()) throw new SQLException("hostname is required");
        if (machineHostname == null || machineHostname.isEmpty()) throw new SQLException("machineHostname is required");
        if (serviceName == null || serviceName.isEmpty()) throw new SQLException("serviceName is required");

        int machineId = resolveOrCreateMachine(conn, machineHostname);
        int serviceId = resolveOrCreateService(conn, machineId, serviceName, "tomcat");

        int existingId = 0;
        try (PreparedStatement check = conn.prepareStatement(
                "SELECT id FROM tomcat_hosts WHERE hostname = ?")) {
            check.setString(1, hostname);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) existingId = rs.getInt(1);
            }
        }

        if (existingId > 0) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE tomcat_hosts SET service_id=?, context_path=COALESCE(?,context_path), " +
                    "app_version=COALESCE(?,app_version), db_name=COALESCE(?,db_name), " +
                    "health_status=COALESCE(?,health_status), health_message=?, health_checked_at=NOW(), " +
                    "status=COALESCE(?,status), notes=COALESCE(?,notes), is_active=1 WHERE id=?")) {
                ps.setInt(1, serviceId);
                ps.setString(2, JsonUtil.extractJsonString(json, "contextPath"));
                ps.setString(3, JsonUtil.extractJsonString(json, "appVersion"));
                ps.setString(4, JsonUtil.extractJsonString(json, "dbName"));
                ps.setString(5, JsonUtil.extractJsonString(json, "healthStatus"));
                ps.setString(6, JsonUtil.extractJsonString(json, "healthMessage"));
                ps.setString(7, JsonUtil.extractJsonString(json, "status"));
                ps.setString(8, JsonUtil.extractJsonString(json, "notes"));
                ps.setInt(9, existingId);
                ps.executeUpdate();
            }
        } else {
            String healthStatus = JsonUtil.extractJsonString(json, "healthStatus");
            String status = JsonUtil.extractJsonString(json, "status");
            String contextPath = JsonUtil.extractJsonString(json, "contextPath");
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO tomcat_hosts (service_id, hostname, context_path, app_version, db_name, " +
                    "health_status, health_message, health_checked_at, status, notes, is_active) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), ?, ?, 1)")) {
                ps.setInt(1, serviceId);
                ps.setString(2, hostname);
                ps.setString(3, contextPath != null ? contextPath : "/InfoCaptionCore");
                ps.setString(4, JsonUtil.extractJsonString(json, "appVersion"));
                ps.setString(5, JsonUtil.extractJsonString(json, "dbName"));
                ps.setString(6, healthStatus != null ? healthStatus : "unknown");
                ps.setString(7, JsonUtil.extractJsonString(json, "healthMessage"));
                ps.setString(8, status != null ? status : "active");
                ps.setString(9, JsonUtil.extractJsonString(json, "notes"));
                ps.executeUpdate();
            }
        }
    }

    // ==================== Resolve helpers ====================

    /**
     * Find machine by hostname or auto-create a minimal entry.
     */
    private int resolveOrCreateMachine(Connection conn, String hostname) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM machines WHERE hostname = ?")) {
            ps.setString(1, hostname);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        // Auto-create
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO machines (hostname, is_active) VALUES (?, 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, hostname);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Failed to create machine: " + hostname);
    }

    /**
     * Find service by machine_id + service_name, or auto-create a minimal entry.
     */
    private int resolveOrCreateService(Connection conn, int machineId, String serviceName, String serviceType) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM machine_services WHERE machine_id = ? AND service_name = ?")) {
            ps.setInt(1, machineId);
            ps.setString(2, serviceName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        // Auto-create
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO machine_services (machine_id, service_type, service_name, status, is_active) " +
                "VALUES (?, ?, ?, 'unknown', 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, machineId);
            ps.setString(2, serviceType);
            ps.setString(3, serviceName);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Failed to create service: " + serviceName);
    }

    // ==================== Auth helper ====================

    /**
     * Authenticate via session (browser) or API key (scripts).
     * Returns true if authenticated, false if 401 sent.
     */
    private boolean authenticateAny(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Check API key first (for script access)
        String apiKey = req.getHeader("X-API-Key");
        if (apiKey != null) {
            String expectedKey = AppConfig.get("api.importKey", SecretsConfig.get("api.importKey", ""));
            if (apiKey.equals(expectedKey)) {
                return true;
            }
        }
        // Fall back to session auth
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            return true;
        }
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType("application/json; charset=UTF-8");
        resp.getWriter().write("{\"error\":\"Not authenticated. Use session or X-API-Key header.\"}");
        return false;
    }

    private User authenticate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Check API key first (for script access — returns a synthetic admin user)
        String apiKey = req.getHeader("X-API-Key");
        if (apiKey != null) {
            String expectedKey = AppConfig.get("api.importKey", SecretsConfig.get("api.importKey", ""));
            if (apiKey.equals(expectedKey)) {
                User apiUser = new User();
                apiUser.setUsername("api-key");
                apiUser.setAdmin(true);
                return apiUser;
            }
        }
        // Fall back to session auth
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write("{\"error\":\"Not authenticated\"}");
            return null;
        }
        User user = (User) session.getAttribute("user");
        // Drift monitor exposes internal infrastructure — restrict to admin users
        if (!user.isAdmin()) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write("{\"error\":\"Admin access required\"}");
            return null;
        }
        return user;
    }

    // ==================== JSON parsing utilities ====================

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

    /**
     * Extract a JSON integer value by key. Returns null if not found.
     */
    private static Integer extractJsonInt(String json, String key) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); }
            catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    /**
     * Extract a JSON array of objects by key. Returns list of JSON object strings.
     * E.g., "machines": [{...}, {...}] → List of "{...}", "{...}"
     */
    private static List<String> extractJsonObjectArray(String json, String key) {
        List<String> result = new ArrayList<>();
        if (json == null) return result;

        // Find the array start: "key": [
        String searchKey = "\"" + key + "\"";
        int keyIdx = json.indexOf(searchKey);
        if (keyIdx < 0) return result;

        int colonIdx = json.indexOf(':', keyIdx + searchKey.length());
        if (colonIdx < 0) return result;

        int bracketIdx = json.indexOf('[', colonIdx);
        if (bracketIdx < 0) return result;

        // Now parse each object {...} in the array with depth tracking
        int depth = 0;
        int objStart = -1;
        boolean inString = false;
        boolean escaped = false;

        for (int i = bracketIdx + 1; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;

            if (c == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    result.add(json.substring(objStart, i + 1));
                    objStart = -1;
                }
            } else if (c == ']' && depth == 0) {
                break;  // End of array
            }
        }
        return result;
    }
}
