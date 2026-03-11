package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.util.AppConfig;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.SmartassicDBUtil;
import com.infocaption.dashboard.util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
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
 * Server List API Servlet.
 *
 * GET  /api/servers        — List all active servers with machine name, version, company
 * GET  /api/servers/health — Async health check (.version.xml) for all active servers
 *
 * Background: Syncs machine_name from smartassic.zsoserver every 10 minutes.
 */
public class ServerListApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static int syncIntervalMinutes() { return AppConfig.getInt("server.syncInterval", 10); }
    private static int httpTimeoutSeconds() { return AppConfig.getInt("server.healthCheckTimeout", 10); }
    private static final int HEALTH_CHECK_OVERALL_TIMEOUT_SECONDS = 60;

    private ScheduledExecutorService scheduler;
    private HttpClient httpClient;

    @Override
    public void init() throws ServletException {
        // Idempotent: add machine_name column if missing
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE servers ADD COLUMN machine_name VARCHAR(255) NULL AFTER url_normalized");
            log("Added machine_name column to servers table");
        } catch (SQLException e) {
            // Error 1060 = Duplicate column name — column already exists, that's fine
            if (e.getErrorCode() != 1060) {
                log("Warning: Could not add machine_name column: " + e.getMessage());
            }
        }

        // Create reusable HttpClient
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(httpTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        // Start background sync for machine names
        int interval = syncIntervalMinutes();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ServerList-MachineNameSync");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                syncMachineNames();
            } catch (Exception e) {
                log("Machine name sync error: " + e.getMessage());
            }
        }, 0, interval, TimeUnit.MINUTES);

        log("ServerListApiServlet initialized — sync every " + interval + " min");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write("{\"error\":\"Not authenticated\"}");
            return;
        }

        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            handleListServers(req, resp);
        } else if (pathInfo.equals("/health")) {
            handleHealthCheck(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    /**
     * GET /api/servers — list all active servers
     */
    private void handleListServers(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String sql =
            "SELECT s.id, s.url, s.url_normalized, s.machine_name, s.current_version, " +
            "       c.company_id, c.company_name " +
            "FROM servers s " +
            "LEFT JOIN customers c ON c.id = s.customer_id " +
            "WHERE s.is_active = 1 " +
            "ORDER BY s.url_normalized";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            while (rs.next()) {
                if (!first) json.append(",");
                first = false;

                json.append("{");
                json.append("\"id\":").append(rs.getInt("id")).append(",");
                json.append("\"url\":").append(JsonUtil.quote(rs.getString("url"))).append(",");
                json.append("\"urlNormalized\":").append(JsonUtil.quote(rs.getString("url_normalized"))).append(",");
                json.append("\"machineName\":").append(JsonUtil.quote(rs.getString("machine_name"))).append(",");
                json.append("\"version\":").append(JsonUtil.quote(rs.getString("current_version"))).append(",");
                json.append("\"companyId\":").append(JsonUtil.quote(rs.getString("company_id"))).append(",");
                json.append("\"companyName\":").append(JsonUtil.quote(rs.getString("company_name")));
                json.append("}");
            }

            json.append("]");
            resp.getWriter().write(json.toString());

        } catch (SQLException e) {
            log("Error listing servers: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    /**
     * GET /api/servers/health — async health check against {url}.version.xml
     */
    private void handleHealthCheck(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        // Load all active server URLs
        List<int[]> serverIds = new ArrayList<>(); // just for pairing
        List<String> serverUrls = new ArrayList<>();

        String sql = "SELECT id, url FROM servers WHERE is_active = 1 ORDER BY url_normalized";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                serverIds.add(new int[]{rs.getInt("id")});
                serverUrls.add(rs.getString("url"));
            }
        } catch (SQLException e) {
            log("Error loading servers for health check: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
            return;
        }

        // Async health check for each server
        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (int i = 0; i < serverUrls.size(); i++) {
            int serverId = serverIds.get(i)[0];
            String url = serverUrls.get(i);
            String checkUrl = buildVersionUrl(url);

            CompletableFuture<String> future = checkServerHealth(serverId, url, checkUrl);
            futures.add(future);
        }

        // Wait for all checks to complete
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(HEALTH_CHECK_OVERALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log("Health check overall timeout — some checks may be incomplete");
        } catch (InterruptedException | ExecutionException e) {
            log("Health check error: " + e.getMessage());
        }

        // Build JSON response
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (CompletableFuture<String> f : futures) {
            if (!first) json.append(",");
            first = false;
            try {
                json.append(f.getNow("{\"serverId\":0,\"url\":null,\"statusCode\":-1,\"severity\":\"severe\",\"error\":\"Check incomplete\"}"));
            } catch (Exception e) {
                json.append("{\"serverId\":0,\"url\":null,\"statusCode\":-1,\"severity\":\"severe\",\"error\":\"Check failed\"}");
            }
        }
        json.append("]");
        resp.getWriter().write(json.toString());
    }

    /**
     * Async check a single server's .version.xml endpoint.
     */
    private CompletableFuture<String> checkServerHealth(int serverId, String serverUrl, String checkUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(checkUrl))
                    .timeout(Duration.ofSeconds(httpTimeoutSeconds()))
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .handle((response, ex) -> {
                        StringBuilder json = new StringBuilder("{");
                        json.append("\"serverId\":").append(serverId).append(",");
                        json.append("\"url\":").append(JsonUtil.quote(serverUrl)).append(",");

                        if (ex != null) {
                            json.append("\"statusCode\":-1,");
                            json.append("\"severity\":\"severe\",");
                            json.append("\"error\":").append(JsonUtil.quote(ex.getMessage()));
                        } else {
                            int code = response.statusCode();
                            String severity = classifySeverity(code);
                            json.append("\"statusCode\":").append(code).append(",");
                            json.append("\"severity\":").append(JsonUtil.quote(severity)).append(",");
                            json.append("\"error\":null");
                        }

                        json.append("}");
                        return json.toString();
                    });
        } catch (Exception e) {
            // Invalid URL or other immediate failure
            StringBuilder json = new StringBuilder("{");
            json.append("\"serverId\":").append(serverId).append(",");
            json.append("\"url\":").append(JsonUtil.quote(serverUrl)).append(",");
            json.append("\"statusCode\":-1,");
            json.append("\"severity\":\"severe\",");
            json.append("\"error\":").append(JsonUtil.quote("Invalid URL: " + e.getMessage()));
            json.append("}");
            return CompletableFuture.completedFuture(json.toString());
        }
    }

    /**
     * Classify HTTP status code into severity level.
     */
    private static String classifySeverity(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) return "ok";
        if (statusCode == 404) return "medium";
        if (statusCode == 401 || statusCode == 403) return "low";
        if (statusCode >= 500) return "severe";
        // Timeout/connection error comes as exception (-1), handled separately
        return "severe"; // anything else unexpected is severe
    }

    /**
     * Build the .version.xml URL from a server URL.
     * Ensures proper trailing slash before appending.
     */
    private static String buildVersionUrl(String url) {
        if (url == null || url.isEmpty()) return "http://invalid/.version.xml";
        String base = url.endsWith("/") ? url : url + "/";
        return base + ".version.xml";
    }

    /**
     * Sync machine names from external smartassic.zsoserver table to local servers table.
     */
    private void syncMachineNames() {
        int updated = 0;
        int errors = 0;

        String selectSql =
            "SELECT ServerUrl, MachineName FROM zsoserver " +
            "WHERE Active = 1 AND MachineName IS NOT NULL AND MachineName != ''";

        String updateSql = "UPDATE servers SET machine_name = ? WHERE url_normalized = ?";

        try (Connection extConn = SmartassicDBUtil.getConnection();
             PreparedStatement selectPs = extConn.prepareStatement(selectSql);
             ResultSet rs = selectPs.executeQuery();
             Connection localConn = DBUtil.getConnection();
             PreparedStatement updatePs = localConn.prepareStatement(updateSql)) {

            while (rs.next()) {
                String serverUrl = rs.getString("ServerUrl");
                String machineName = rs.getString("MachineName");

                String normalized = normalizeUrl(serverUrl);
                if (normalized.isEmpty()) continue;

                try {
                    updatePs.setString(1, machineName);
                    updatePs.setString(2, normalized);
                    int rows = updatePs.executeUpdate();
                    if (rows > 0) updated++;
                } catch (SQLException e) {
                    errors++;
                }
            }

            if (errors > 0) {
                log("Machine name sync: " + updated + " updated, " + errors + " errors");
            }

        } catch (SQLException e) {
            log("Machine name sync failed (DB connection): " + e.getMessage());
        }
    }

    /**
     * Normalize a URL for matching: lowercase, strip protocol and path.
     * Same logic as CustomerStatsApiServlet.normalizeUrl().
     */
    private static String normalizeUrl(String url) {
        if (url == null) return "";
        return url.toLowerCase()
                .replaceAll("^https?://", "")
                .replaceAll("/.*$", "")
                .replaceAll("/$", "");
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            log("Machine name sync scheduler stopped");
        }
    }
}
