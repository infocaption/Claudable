package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.util.AppConfig;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.JsonUtil;
import com.infocaption.dashboard.util.WebPushUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Background health check scheduler.
 *
 * Periodically checks all active servers using either:
 *   - "legacy" — HTTP GET to {url}/.version.xml (same as ServerListApiServlet)
 *   - "drift"  — reads health_status from tomcat_hosts table
 *   - "both"   — runs both check types
 *
 * Tracks state in server_health_state table. When a server transitions
 * from ok/medium/low → severe (green→red), sends a push notification
 * to all subscribers.
 *
 * Configurable via AppConfig (Admin > Inställningar):
 *   healthmonitor.enabled         — on/off
 *   healthmonitor.intervalMinutes — check interval
 *   healthmonitor.checkType       — legacy, drift, both
 *   healthmonitor.notifyOnRedOnly — only notify on transitions to red
 */
public class HealthCheckScheduler extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(HealthCheckScheduler.class);

    private static final String FALLBACK_ENABLED = "true";
    private static final String FALLBACK_INTERVAL = "5";
    private static final String FALLBACK_CHECK_TYPE = "legacy";
    private static final int HTTP_TIMEOUT_SECONDS = 10;
    private static final int OVERALL_TIMEOUT_SECONDS = 120;

    private ScheduledExecutorService scheduler;
    private HttpClient httpClient;

    // ==================== Init ====================

    @Override
    public void init() throws ServletException {
        // Create health state table
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS server_health_state (" +
                "  server_id       INT NOT NULL PRIMARY KEY," +
                "  check_type      ENUM('legacy','drift') NOT NULL DEFAULT 'legacy'," +
                "  last_status     VARCHAR(20)  NOT NULL DEFAULT 'unknown'," +
                "  last_http_code  INT          NOT NULL DEFAULT 0," +
                "  last_error      VARCHAR(500) NULL," +
                "  last_checked    TIMESTAMP    NULL," +
                "  changed_at      TIMESTAMP    NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
        } catch (SQLException e) {
            log.warn("Could not create server_health_state table: {}", e.getMessage());
        }

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        startScheduler();
        log.info("HealthCheckScheduler initialized");
    }

    private void startScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HealthCheckScheduler");
            t.setDaemon(true);
            return t;
        });

        // Initial delay 1 min (let other servlets start first), then repeat
        scheduler.scheduleAtFixedRate(() -> {
            try {
                runHealthChecks();
            } catch (Exception e) {
                log.error("Health check cycle failed: {}", e.getMessage());
            }
        }, 1, getIntervalMinutes(), TimeUnit.MINUTES);
    }

    private int getIntervalMinutes() {
        return Math.max(1, AppConfig.getInt("healthmonitor.intervalMinutes",
                Integer.parseInt(FALLBACK_INTERVAL)));
    }

    // ==================== Main Check Cycle ====================

    private void runHealthChecks() {
        String enabled = AppConfig.get("healthmonitor.enabled", FALLBACK_ENABLED);
        if (!"true".equalsIgnoreCase(enabled)) return;

        String checkType = AppConfig.get("healthmonitor.checkType", FALLBACK_CHECK_TYPE).toLowerCase();
        log.debug("Running health checks (type={})", checkType);

        if ("legacy".equals(checkType) || "both".equals(checkType)) {
            runLegacyChecks();
        }
        if ("drift".equals(checkType) || "both".equals(checkType)) {
            runDriftChecks();
        }
    }

    // ==================== Legacy Checks (.version.xml) ====================

    private void runLegacyChecks() {
        // Load all active servers
        List<ServerEntry> servers = new ArrayList<>();
        String sql = "SELECT id, url FROM servers WHERE is_active = 1";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                servers.add(new ServerEntry(rs.getInt("id"), rs.getString("url"), null));
            }
        } catch (SQLException e) {
            log.error("Failed to load servers for legacy health check: {}", e.getMessage());
            return;
        }

        if (servers.isEmpty()) return;

        // Async health check for each server
        List<CompletableFuture<HealthResult>> futures = new ArrayList<>();
        for (ServerEntry server : servers) {
            futures.add(checkLegacyHealth(server));
        }

        // Wait for all
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(OVERALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Legacy health check timeout/error: {}", e.getMessage());
        }

        // Process results
        for (CompletableFuture<HealthResult> f : futures) {
            try {
                HealthResult result = f.getNow(null);
                if (result != null) {
                    processHealthResult(result, "legacy");
                }
            } catch (Exception e) {
                log.warn("Error processing health result: {}", e.getMessage());
            }
        }
    }

    private CompletableFuture<HealthResult> checkLegacyHealth(ServerEntry server) {
        String checkUrl = buildVersionUrl(server.url);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(checkUrl))
                    .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .handle((response, ex) -> {
                        if (ex != null) {
                            return new HealthResult(server.id, server.url, "severe", -1,
                                    ex.getMessage() != null ? ex.getMessage() : "Connection failed");
                        }
                        int code = response.statusCode();
                        return new HealthResult(server.id, server.url, classifySeverity(code), code, null);
                    });
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    new HealthResult(server.id, server.url, "severe", -1, "Invalid URL: " + e.getMessage()));
        }
    }

    // ==================== Drift Checks (tomcat_hosts table) ====================

    private void runDriftChecks() {
        // Read health from tomcat_hosts joined with machine_services and machines,
        // then map back to servers via hostname matching
        String sql =
            "SELECT s.id AS server_id, s.url, th.health_status, th.health_message " +
            "FROM tomcat_hosts th " +
            "JOIN machine_services ms ON th.service_id = ms.id " +
            "JOIN machines m ON ms.machine_id = m.id " +
            "JOIN servers s ON s.url_normalized = LOWER(th.hostname) " +
            "WHERE th.is_active = 1 AND s.is_active = 1";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int serverId = rs.getInt("server_id");
                String url = rs.getString("url");
                String healthStatus = rs.getString("health_status");
                String healthMessage = rs.getString("health_message");

                // Map drift health_status to our severity scale
                String severity;
                switch (healthStatus != null ? healthStatus : "unknown") {
                    case "ok":      severity = "ok"; break;
                    case "warning": severity = "medium"; break;
                    case "error":   severity = "severe"; break;
                    default:        severity = "unknown"; break;
                }

                HealthResult result = new HealthResult(serverId, url, severity, 0, healthMessage);
                processHealthResult(result, "drift");
            }
        } catch (SQLException e) {
            // tomcat_hosts table might not exist if drift monitor isn't used
            log.debug("Drift health check skipped (table may not exist): {}", e.getMessage());
        }
    }

    // ==================== State Tracking & Notification ====================

    private void processHealthResult(HealthResult result, String checkType) {
        String previousStatus = getPreviousStatus(result.serverId);
        boolean transitionToRed = isTransitionToRed(previousStatus, result.status);

        // Update state in DB
        updateHealthState(result, checkType);

        // Auto-resolve CloudGuard incident if server recovered
        autoResolveIfRecovered(result, previousStatus);

        String notifyOnRedOnly = AppConfig.get("healthmonitor.notifyOnRedOnly", "true");

        if (transitionToRed) {
            // Always notify on green→red transition
            log.warn("Server {} ({}) went RED (was: {}, now: {})",
                    result.serverId, result.url, previousStatus, result.status);
            sendHealthAlert(result);
        } else if (!"true".equalsIgnoreCase(notifyOnRedOnly)
                && !result.status.equals(previousStatus)
                && !"unknown".equals(previousStatus)) {
            // Notify on any state change if configured to do so
            sendHealthAlert(result);
        }
    }

    private String getPreviousStatus(int serverId) {
        String sql = "SELECT last_status FROM server_health_state WHERE server_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("last_status");
            }
        } catch (SQLException e) {
            log.debug("Error reading previous status for server {}: {}", serverId, e.getMessage());
        }
        return "unknown";
    }

    private boolean isTransitionToRed(String previous, String current) {
        // "severe" is red. Transition FROM anything non-severe TO severe = alert
        if (!"severe".equals(current)) return false;
        return !"severe".equals(previous) && !"unknown".equals(previous);
    }

    private void updateHealthState(HealthResult result, String checkType) {
        String sql =
            "INSERT INTO server_health_state (server_id, check_type, last_status, last_http_code, last_error, last_checked, changed_at) " +
            "VALUES (?, ?, ?, ?, ?, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE " +
            "  check_type = VALUES(check_type)," +
            "  changed_at = IF(last_status != VALUES(last_status), NOW(), changed_at)," +
            "  last_status = VALUES(last_status)," +
            "  last_http_code = VALUES(last_http_code)," +
            "  last_error = VALUES(last_error)," +
            "  last_checked = NOW()";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, result.serverId);
            ps.setString(2, checkType);
            ps.setString(3, result.status);
            ps.setInt(4, result.httpCode);
            ps.setString(5, result.error);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("Failed to update health state for server {}: {}", result.serverId, e.getMessage());
        }
    }

    private void sendHealthAlert(HealthResult result) {
        // Create a CloudGuard incident for the alert
        String severity = "severe".equals(result.status) ? "critical" : "warning";
        String entityName = result.url != null ? result.url : "Server #" + result.serverId;
        String message = "Server went " + result.status.toUpperCase();
        if (result.error != null && !result.error.isEmpty()) {
            message += ": " + result.error;
        } else if (result.httpCode > 0) {
            message += " (HTTP " + result.httpCode + ")";
        }

        // Report as CloudGuard incident
        int incidentId = createCloudGuardIncident(entityName, severity, message);

        // Push notification to subscribers
        if (incidentId > 0) {
            WebPushUtil.notifySubscribersAsync(incidentId, entityName, severity, message);
        }
    }

    private int createCloudGuardIncident(String entityName, String severity, String message) {
        // Check if there's already an active incident for this entity to avoid duplicates
        String checkSql = "SELECT id FROM cloudguard WHERE entity_name = ? AND is_active = 1 LIMIT 1";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, entityName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // Already an active incident — don't create duplicate
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            log.warn("Error checking existing incident: {}", e.getMessage());
        }

        String sql = "INSERT INTO cloudguard (entity_name, entity_type, severity, message, reported_by) " +
                     "VALUES (?, 'server', ?, ?, 'HealthCheckScheduler')";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, entityName);
            ps.setString(2, severity);
            ps.setString(3, message);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            log.error("Failed to create CloudGuard incident: {}", e.getMessage());
        }
        return 0;
    }

    // ==================== Auto-resolve when server comes back ====================

    /**
     * Called after processHealthResult — if a server was severe and is now ok,
     * auto-resolve any active CloudGuard incident for it.
     */
    private void autoResolveIfRecovered(HealthResult result, String previousStatus) {
        if ("ok".equals(result.status) && "severe".equals(previousStatus)) {
            String sql = "UPDATE cloudguard SET is_active = 0, resolved_at = NOW(), " +
                         "resolved_by = 'HealthCheckScheduler', resolution_note = 'Auto-resolved: server recovered' " +
                         "WHERE entity_name = ? AND is_active = 1 AND reported_by = 'HealthCheckScheduler'";
            try (Connection conn = DBUtil.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, result.url);
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    log.info("Auto-resolved {} CloudGuard incident(s) for recovered server {}", rows, result.url);
                }
            } catch (SQLException e) {
                log.warn("Error auto-resolving incident for {}: {}", result.url, e.getMessage());
            }
        }
    }

    // ==================== Helpers ====================

    private static String classifySeverity(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) return "ok";
        if (statusCode == 404) return "medium";
        if (statusCode == 401 || statusCode == 403) return "low";
        if (statusCode >= 500) return "severe";
        return "severe";
    }

    private static String buildVersionUrl(String url) {
        if (url == null || url.isEmpty()) return "http://invalid/.version.xml";
        String base = url.endsWith("/") ? url : url + "/";
        return base + ".version.xml";
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            log.info("HealthCheckScheduler stopped");
        }
    }

    // ==================== Inner Classes ====================

    private static class ServerEntry {
        final int id;
        final String url;
        final String urlNormalized;

        ServerEntry(int id, String url, String urlNormalized) {
            this.id = id;
            this.url = url;
            this.urlNormalized = urlNormalized;
        }
    }

    private static class HealthResult {
        final int serverId;
        final String url;
        final String status; // ok, low, medium, severe, unknown
        final int httpCode;
        final String error;

        HealthResult(int serverId, String url, String status, int httpCode, String error) {
            this.serverId = serverId;
            this.url = url;
            this.status = status;
            this.httpCode = httpCode;
            this.error = error;
        }
    }
}
