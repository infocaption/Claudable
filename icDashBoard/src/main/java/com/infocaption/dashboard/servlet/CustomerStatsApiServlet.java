package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.AdminUtil;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.JsonUtil;
import com.infocaption.dashboard.util.SmartassicDBUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Customer Statistics API Servlet.
 *
 * GET  /api/customer-stats          — List all customers with stats for a date range
 *      ?from=yyyy-MM-dd&to=yyyy-MM-dd  (optional, defaults to latest snapshot)
 *
 * GET  /api/customer-stats/history  — Historical snapshots for one customer
 *      ?url=X&from=yyyy-MM-dd&to=yyyy-MM-dd&limit=365
 *
 * POST /api/customer-stats/import   — Bulk import (JSON array), authenticated via X-API-Key header
 */
public class CustomerStatsApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(CustomerStatsApiServlet.class);

    @Override
    public void init() throws ServletException {
        // Auto-create tables if they don't exist (idempotent)
        // Schema: customers (companies) → servers (URLs) → customer_stats_daily (per server)
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS customers (" +
                "  id              INT AUTO_INCREMENT PRIMARY KEY," +
                "  company_id      VARCHAR(50)  NULL COMMENT 'SuperOffice company ID'," +
                "  company_name    VARCHAR(255) NOT NULL," +
                "  coach_email     VARCHAR(255) NULL COMMENT 'Kundcoach epost'," +
                "  track           VARCHAR(100) NULL COMMENT 'Engagement track: map,train,assist,guide'," +
                "  is_active       TINYINT(1)   NOT NULL DEFAULT 1," +
                "  first_seen      DATE         NOT NULL," +
                "  last_seen       DATE         NOT NULL," +
                "  created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  UNIQUE INDEX idx_customer_company_id (company_id)," +
                "  INDEX idx_customer_active (is_active)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS servers (" +
                "  id              INT AUTO_INCREMENT PRIMARY KEY," +
                "  customer_id     INT          NULL COMMENT 'FK to customers'," +
                "  url             VARCHAR(500) NOT NULL," +
                "  url_normalized  VARCHAR(500) NOT NULL," +
                "  current_version VARCHAR(20)  NULL," +
                "  first_seen      DATE         NOT NULL," +
                "  last_seen       DATE         NOT NULL," +
                "  is_active       TINYINT(1)   NOT NULL DEFAULT 1," +
                "  is_excluded     TINYINT(1)   NOT NULL DEFAULT 0," +
                "  created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  UNIQUE INDEX idx_server_url_norm (url_normalized)," +
                "  INDEX idx_server_customer (customer_id)," +
                "  INDEX idx_server_active (is_active)," +
                "  INDEX idx_server_excluded (is_excluded)," +
                "  CONSTRAINT fk_server_customer FOREIGN KEY (customer_id)" +
                "    REFERENCES customers(id) ON DELETE SET NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS customer_stats_daily (" +
                "  id                          BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  server_id                   INT         NOT NULL," +
                "  snapshot_date               DATE        NOT NULL," +
                "  publiseringar_30d           INT         NOT NULL DEFAULT 0," +
                "  publiseringar_30_60d        INT         NOT NULL DEFAULT 0," +
                "  skapade_guider_30d          INT         NOT NULL DEFAULT 0," +
                "  skapade_guider_30_60d       INT         NOT NULL DEFAULT 0," +
                "  visningar_30d               INT         NOT NULL DEFAULT 0," +
                "  visningar_30_60d            INT         NOT NULL DEFAULT 0," +
                "  processvisningar_30d        INT         NOT NULL DEFAULT 0," +
                "  processvisningar_30_60d     INT         NOT NULL DEFAULT 0," +
                "  processer_skapade_30d       INT         NOT NULL DEFAULT 0," +
                "  processer_skapade_30_60d    INT         NOT NULL DEFAULT 0," +
                "  antal_producenter           INT         NOT NULL DEFAULT 0," +
                "  antal_administratorer       INT         NOT NULL DEFAULT 0," +
                "  totalt_antal_anvandare      INT         NOT NULL DEFAULT 0," +
                "  antal_aktiva_producenter_6m INT         NOT NULL DEFAULT 0," +
                "  version                     VARCHAR(20) NULL," +
                "  created_at                  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP," +
                "  UNIQUE INDEX idx_server_date (server_id, snapshot_date)," +
                "  INDEX idx_date (snapshot_date)," +
                "  CONSTRAINT fk_stats_server FOREIGN KEY (server_id)" +
                "    REFERENCES servers(id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS license_keys (" +
                "  id              INT AUTO_INCREMENT PRIMARY KEY," +
                "  server_id       INT NULL," +
                "  license_key_id  VARCHAR(50) NOT NULL COMMENT 'SuperOffice licenseKeyID'," +
                "  filename        VARCHAR(255) NULL COMMENT 'keyName (license key name)'," +
                "  server_url      VARCHAR(500) NULL COMMENT 'url (raw URL from SuperOffice)'," +
                "  server_version  VARCHAR(50) NULL COMMENT 'Reserved'," +
                "  license_holder  VARCHAR(255) NULL COMMENT 'holder (license holder name)'," +
                "  expiration_date VARCHAR(50) NULL COMMENT 'Reserved'," +
                "  train           TINYINT(1) DEFAULT 0 COMMENT 'Train track (0/1)'," +
                "  assist          TINYINT(1) DEFAULT 0 COMMENT 'Assist track (0/1)'," +
                "  map             TINYINT(1) DEFAULT 0 COMMENT 'Map track (0/1)'," +
                "  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  UNIQUE INDEX idx_license_key_id (license_key_id)," +
                "  INDEX idx_license_server (server_id)," +
                "  CONSTRAINT fk_license_server FOREIGN KEY (server_id)" +
                "    REFERENCES servers(id) ON DELETE SET NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            log("Customer stats tables verified/created successfully");
        } catch (SQLException e) {
            log("Warning: Could not auto-create customer stats tables: " + e.getMessage());
            // Don't fail startup — tables might already exist or DB might be temporarily unavailable
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Auth check (same pattern as ModuleApiServlet)
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write("{\"error\":\"Not authenticated\"}");
            return;
        }

        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String pathInfo = req.getPathInfo(); // null for /api/customer-stats, "/history" for .../history
        if (pathInfo == null || pathInfo.equals("/")) {
            handleListStats(req, resp);
        } else if (pathInfo.equals("/history")) {
            handleHistory(req, resp);
        } else if (pathInfo.equals("/servers")) {
            handleListServers(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.equals("/import")) {
            handleImport(req, resp);
        } else if (pathInfo != null && pathInfo.equals("/servers/exclude")) {
            handleToggleExclude(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write("{\"error\":\"Method not allowed\"}");
        }
    }

    /**
     * GET /api/customer-stats?from=...&to=...
     *
     * Returns JSON matching allstats.json format plus companyId, companyName, snapshotDate.
     * Activity metrics (publiseringar, guider, visningar, etc.) are aggregated (SUMmed)
     * over the period. Point-in-time counts (producenter, användare, etc.) come from
     * the latest snapshot in the period.
     *
     * When from/to provided: aggregates [from,to] as current, equally long period before as previous.
     * When no dates: aggregates last 30 days as current, day 31-60 as previous.
     */
    private void handleListStats(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        long startTime = System.currentTimeMillis();
        String fromParam = req.getParameter("from");
        String toParam = req.getParameter("to");

        try (Connection conn = DBUtil.getConnection()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            if (fromParam != null && toParam != null) {
                // Date range mode: compare current period vs previous period
                LocalDate toDate, fromDate;
                try {
                    fromDate = LocalDate.parse(fromParam);
                    toDate = LocalDate.parse(toParam);
                } catch (DateTimeParseException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"error\":\"Invalid date format. Use yyyy-MM-dd\"}");
                    return;
                }

                long periodDays = ChronoUnit.DAYS.between(fromDate, toDate);
                LocalDate prevTo = fromDate.minusDays(1);
                LocalDate prevFrom = prevTo.minusDays(periodDays);

                // Date range mode: aggregate daily rows in [from, to] as current period,
                // and equally long period before 'from' as previous period.
                // Activity metrics are SUMmed; point-in-time counts from latest snapshot in period.
                // Stats are per-server (URL); company info joined from customers table.
                // NOTE: Uses derived table (latest_dates) instead of correlated subquery for
                // the latest snapshot lookup — ~20x faster on MySQL 5.7 (0.4s vs 8s).
                String sql =
                    "SELECT " +
                    "  s.id, s.url, c.company_id, c.company_name, c.coach_email, u_coach.full_name AS coach_name, " +
                    "  MAX(curr.snapshot_date) AS current_snapshot_date, " +
                    "  SUM(curr.publiseringar_30d) AS publiseringar_30d, " +
                    "  SUM(curr.skapade_guider_30d) AS skapade_guider_30d, " +
                    "  SUM(curr.visningar_30d) AS visningar_30d, " +
                    "  SUM(curr.processvisningar_30d) AS processvisningar_30d, " +
                    "  SUM(curr.processer_skapade_30d) AS processer_skapade_30d, " +
                    "  SUM(curr.antal_inloggningar_prod_admin) AS antal_inloggningar_prod_admin, " +
                    "  SUM(curr.antal_unika_inloggade_prod_admin) AS antal_unika_inloggade_prod_admin, " +
                    "  SUM(curr.antal_unika_publicerande_prod) AS antal_unika_publicerande_prod, " +
                    "  latest.antal_producenter, " +
                    "  latest.antal_administratorer, " +
                    "  latest.totalt_antal_anvandare, " +
                    "  latest.antal_aktiva_producenter_6m, " +
                    "  latest.version, " +
                    "  IFNULL(prev_agg.prev_publiseringar, 0) AS prev_publiseringar, " +
                    "  IFNULL(prev_agg.prev_guider, 0) AS prev_guider, " +
                    "  IFNULL(prev_agg.prev_visningar, 0) AS prev_visningar, " +
                    "  IFNULL(prev_agg.prev_processvisningar, 0) AS prev_processvisningar, " +
                    "  IFNULL(prev_agg.prev_processer, 0) AS prev_processer, " +
                    "  IFNULL(prev_agg.prev_inloggningar, 0) AS prev_inloggningar, " +
                    "  IFNULL(prev_agg.prev_unika_inloggade, 0) AS prev_unika_inloggade, " +
                    "  IFNULL(prev_agg.prev_unika_publicerande, 0) AS prev_unika_publicerande, " +
                    // License data (correlated subqueries)
                    "  (SELECT COUNT(*) FROM license_keys lk WHERE lk.server_id = s.id) AS license_count, " +
                    "  (SELECT MIN(lk2.expiration_date) FROM license_keys lk2 WHERE lk2.server_id = s.id) AS license_nearest_expiry, " +
                    "  (SELECT lk3.license_holder FROM license_keys lk3 WHERE lk3.server_id = s.id ORDER BY lk3.id LIMIT 1) AS license_holder, " +
                    // Track data derived from license_keys (train/assist/map flags per license, aggregated per server)
                    "  (SELECT CONCAT_WS(',', " +
                    "    IF(MAX(lkt.map) = 1, 'map', NULL), " +
                    "    IF(MAX(lkt.train) = 1, 'train', NULL), " +
                    "    IF(MAX(lkt.assist) = 1, 'assist', NULL) " +
                    "  ) FROM license_keys lkt WHERE lkt.server_id = s.id) AS track, " +
                    "  c.is_onboarding, " +
                    "  c.renewal_date, " +
                    "  c.leadscore, " +
                    "  c.engagement, " +
                    "  c.upsell " +
                    "FROM servers s " +
                    "LEFT JOIN customers c ON c.id = s.customer_id " +
                    "LEFT JOIN users u_coach ON u_coach.email = c.coach_email AND u_coach.is_active = 1 " +
                    // Current period: [from, to]
                    "LEFT JOIN customer_stats_daily curr ON curr.server_id = s.id " +
                    "  AND curr.snapshot_date BETWEEN ? AND ? " +
                    // Latest snapshot date per server (materialized once, not per-row)
                    "LEFT JOIN ( " +
                    "  SELECT server_id, MAX(snapshot_date) AS max_date " +
                    "  FROM customer_stats_daily " +
                    "  WHERE snapshot_date BETWEEN ? AND ? " +
                    "  GROUP BY server_id " +
                    ") latest_dates ON latest_dates.server_id = s.id " +
                    // Point-in-time counts from the latest snapshot
                    "LEFT JOIN customer_stats_daily latest ON latest.server_id = s.id " +
                    "  AND latest.snapshot_date = latest_dates.max_date " +
                    // Previous period aggregated
                    "LEFT JOIN ( " +
                    "  SELECT server_id, " +
                    "    SUM(publiseringar_30d) AS prev_publiseringar, " +
                    "    SUM(skapade_guider_30d) AS prev_guider, " +
                    "    SUM(visningar_30d) AS prev_visningar, " +
                    "    SUM(processvisningar_30d) AS prev_processvisningar, " +
                    "    SUM(processer_skapade_30d) AS prev_processer, " +
                    "    SUM(antal_inloggningar_prod_admin) AS prev_inloggningar, " +
                    "    SUM(antal_unika_inloggade_prod_admin) AS prev_unika_inloggade, " +
                    "    SUM(antal_unika_publicerande_prod) AS prev_unika_publicerande " +
                    "  FROM customer_stats_daily " +
                    "  WHERE snapshot_date BETWEEN ? AND ? " +
                    "  GROUP BY server_id " +
                    ") prev_agg ON prev_agg.server_id = s.id " +
                    "WHERE s.is_active = 1 AND s.is_excluded = 0 " +
                    "GROUP BY s.id, s.url, c.company_id, c.company_name, c.coach_email, u_coach.full_name, " +
                    "  c.is_onboarding, c.renewal_date, c.leadscore, c.engagement, c.upsell, " +
                    "  latest.antal_producenter, latest.antal_administratorer, " +
                    "  latest.totalt_antal_anvandare, latest.antal_aktiva_producenter_6m, " +
                    "  latest.version, " +
                    "  prev_agg.prev_publiseringar, prev_agg.prev_guider, " +
                    "  prev_agg.prev_visningar, prev_agg.prev_processvisningar, " +
                    "  prev_agg.prev_processer, " +
                    "  prev_agg.prev_inloggningar, prev_agg.prev_unika_inloggade, " +
                    "  prev_agg.prev_unika_publicerande " +
                    "ORDER BY s.url_normalized";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setDate(1, Date.valueOf(fromDate));
                    ps.setDate(2, Date.valueOf(toDate));
                    ps.setDate(3, Date.valueOf(fromDate));
                    ps.setDate(4, Date.valueOf(toDate));
                    ps.setDate(5, Date.valueOf(prevFrom));
                    ps.setDate(6, Date.valueOf(prevTo));

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            if (!first) json.append(",");
                            first = false;
                            appendCustomerJson(json, rs);
                        }
                    }
                }

            } else {
                // No date range: aggregate last 30 days vs previous 30 days (day 31-60)
                // Activity metrics (publiseringar, guider, etc.) are SUMmed over the period.
                // Point-in-time counts (producenter, användare, etc.) come from the latest snapshot.
                // Stats are per-server (URL); company info joined from customers table.
                // NOTE: Uses derived table (latest_dates) instead of correlated subquery.
                String sql =
                    "SELECT " +
                    "  s.id, s.url, c.company_id, c.company_name, c.coach_email, u_coach.full_name AS coach_name, " +
                    "  MAX(curr.snapshot_date) AS current_snapshot_date, " +
                    "  SUM(curr.publiseringar_30d) AS publiseringar_30d, " +
                    "  SUM(curr.skapade_guider_30d) AS skapade_guider_30d, " +
                    "  SUM(curr.visningar_30d) AS visningar_30d, " +
                    "  SUM(curr.processvisningar_30d) AS processvisningar_30d, " +
                    "  SUM(curr.processer_skapade_30d) AS processer_skapade_30d, " +
                    "  SUM(curr.antal_inloggningar_prod_admin) AS antal_inloggningar_prod_admin, " +
                    "  SUM(curr.antal_unika_inloggade_prod_admin) AS antal_unika_inloggade_prod_admin, " +
                    "  SUM(curr.antal_unika_publicerande_prod) AS antal_unika_publicerande_prod, " +
                    "  latest.antal_producenter, " +
                    "  latest.antal_administratorer, " +
                    "  latest.totalt_antal_anvandare, " +
                    "  latest.antal_aktiva_producenter_6m, " +
                    "  latest.version, " +
                    "  IFNULL(prev_agg.prev_publiseringar, 0) AS prev_publiseringar, " +
                    "  IFNULL(prev_agg.prev_guider, 0) AS prev_guider, " +
                    "  IFNULL(prev_agg.prev_visningar, 0) AS prev_visningar, " +
                    "  IFNULL(prev_agg.prev_processvisningar, 0) AS prev_processvisningar, " +
                    "  IFNULL(prev_agg.prev_processer, 0) AS prev_processer, " +
                    "  IFNULL(prev_agg.prev_inloggningar, 0) AS prev_inloggningar, " +
                    "  IFNULL(prev_agg.prev_unika_inloggade, 0) AS prev_unika_inloggade, " +
                    "  IFNULL(prev_agg.prev_unika_publicerande, 0) AS prev_unika_publicerande, " +
                    // License data (correlated subqueries)
                    "  (SELECT COUNT(*) FROM license_keys lk WHERE lk.server_id = s.id) AS license_count, " +
                    "  (SELECT MIN(lk2.expiration_date) FROM license_keys lk2 WHERE lk2.server_id = s.id) AS license_nearest_expiry, " +
                    "  (SELECT lk3.license_holder FROM license_keys lk3 WHERE lk3.server_id = s.id ORDER BY lk3.id LIMIT 1) AS license_holder, " +
                    // Track data derived from license_keys (train/assist/map flags per license, aggregated per server)
                    "  (SELECT CONCAT_WS(',', " +
                    "    IF(MAX(lkt.map) = 1, 'map', NULL), " +
                    "    IF(MAX(lkt.train) = 1, 'train', NULL), " +
                    "    IF(MAX(lkt.assist) = 1, 'assist', NULL) " +
                    "  ) FROM license_keys lkt WHERE lkt.server_id = s.id) AS track, " +
                    "  c.is_onboarding, " +
                    "  c.renewal_date, " +
                    "  c.leadscore, " +
                    "  c.engagement, " +
                    "  c.upsell " +
                    "FROM servers s " +
                    "LEFT JOIN customers c ON c.id = s.customer_id " +
                    "LEFT JOIN users u_coach ON u_coach.email = c.coach_email AND u_coach.is_active = 1 " +
                    // Current period: last 30 days
                    "LEFT JOIN customer_stats_daily curr ON curr.server_id = s.id " +
                    "  AND curr.snapshot_date > DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
                    "  AND curr.snapshot_date <= CURDATE() " +
                    // Latest snapshot date per server (materialized once)
                    "LEFT JOIN ( " +
                    "  SELECT server_id, MAX(snapshot_date) AS max_date " +
                    "  FROM customer_stats_daily " +
                    "  GROUP BY server_id " +
                    ") latest_dates ON latest_dates.server_id = s.id " +
                    // Point-in-time counts from the latest snapshot
                    "LEFT JOIN customer_stats_daily latest ON latest.server_id = s.id " +
                    "  AND latest.snapshot_date = latest_dates.max_date " +
                    // Previous period (day 31-60) aggregated
                    "LEFT JOIN ( " +
                    "  SELECT server_id, " +
                    "    SUM(publiseringar_30d) AS prev_publiseringar, " +
                    "    SUM(skapade_guider_30d) AS prev_guider, " +
                    "    SUM(visningar_30d) AS prev_visningar, " +
                    "    SUM(processvisningar_30d) AS prev_processvisningar, " +
                    "    SUM(processer_skapade_30d) AS prev_processer, " +
                    "    SUM(antal_inloggningar_prod_admin) AS prev_inloggningar, " +
                    "    SUM(antal_unika_inloggade_prod_admin) AS prev_unika_inloggade, " +
                    "    SUM(antal_unika_publicerande_prod) AS prev_unika_publicerande " +
                    "  FROM customer_stats_daily " +
                    "  WHERE snapshot_date > DATE_SUB(CURDATE(), INTERVAL 60 DAY) " +
                    "    AND snapshot_date <= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
                    "  GROUP BY server_id " +
                    ") prev_agg ON prev_agg.server_id = s.id " +
                    "WHERE s.is_active = 1 AND s.is_excluded = 0 " +
                    "GROUP BY s.id, s.url, c.company_id, c.company_name, c.coach_email, u_coach.full_name, " +
                    "  c.is_onboarding, c.renewal_date, c.leadscore, c.engagement, c.upsell, " +
                    "  latest.antal_producenter, latest.antal_administratorer, " +
                    "  latest.totalt_antal_anvandare, latest.antal_aktiva_producenter_6m, " +
                    "  latest.version, " +
                    "  prev_agg.prev_publiseringar, prev_agg.prev_guider, " +
                    "  prev_agg.prev_visningar, prev_agg.prev_processvisningar, " +
                    "  prev_agg.prev_processer, " +
                    "  prev_agg.prev_inloggningar, prev_agg.prev_unika_inloggade, " +
                    "  prev_agg.prev_unika_publicerande " +
                    "ORDER BY s.url_normalized";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            if (!first) json.append(",");
                            first = false;
                            appendCustomerJson(json, rs);
                        }
                    }
                }
            }

            json.append("]");
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Customer stats query completed in {}ms ({} chars)", elapsed, json.length());
            PrintWriter out = resp.getWriter();
            out.write(json.toString());
            out.flush();

        } catch (SQLException e) {
            log.error("Failed to load customer statistics", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    /**
     * Append a single customer JSON object to the StringBuilder.
     * Field names match the allstats.json format for frontend compatibility.
     */
    private void appendCustomerJson(StringBuilder json, ResultSet rs) throws SQLException {
        String url = rs.getString("url");
        String companyId = rs.getString("company_id");
        String companyName = rs.getString("company_name");
        String coachEmail = rs.getString("coach_email");
        String coachName = rs.getString("coach_name");
        String track = rs.getString("track");
        int isOnboarding = rs.getInt("is_onboarding");
        Date renewalDate = rs.getDate("renewal_date");
        int leadscore = rs.getInt("leadscore");
        boolean leadscoreNull = rs.wasNull();
        String engagement = rs.getString("engagement");
        String upsell = rs.getString("upsell");
        int licenseCount = rs.getInt("license_count");
        String licenseExpiry = rs.getString("license_nearest_expiry");
        String licenseHolder = rs.getString("license_holder");
        String version = rs.getString("version");
        Date snapshotDate = rs.getDate("current_snapshot_date");

        json.append("{");
        json.append("\"url\":").append(JsonUtil.quote(url)).append(",");
        json.append("\"companyId\":").append(JsonUtil.quote(companyId)).append(",");
        json.append("\"companyName\":").append(JsonUtil.quote(companyName)).append(",");
        json.append("\"coachEmail\":").append(JsonUtil.quote(coachEmail)).append(",");
        json.append("\"coachName\":").append(JsonUtil.quote(coachName)).append(",");
        json.append("\"track\":").append(JsonUtil.quote(track)).append(",");
        json.append("\"isOnboarding\":").append(isOnboarding).append(",");
        json.append("\"renewalDate\":").append(JsonUtil.quote(renewalDate != null ? renewalDate.toString() : null)).append(",");
        json.append("\"leadscore\":").append(leadscoreNull ? "null" : leadscore).append(",");
        json.append("\"engagement\":").append(JsonUtil.quote(engagement)).append(",");
        json.append("\"upsell\":").append(JsonUtil.quote(upsell)).append(",");
        json.append("\"licenseCount\":").append(licenseCount).append(",");
        json.append("\"licenseExpiry\":").append(JsonUtil.quote(licenseExpiry)).append(",");
        json.append("\"licenseHolder\":").append(JsonUtil.quote(licenseHolder)).append(",");
        json.append("\"Version\":").append(JsonUtil.quote(version)).append(",");
        json.append("\"snapshotDate\":").append(JsonUtil.quote(snapshotDate != null ? snapshotDate.toString() : null)).append(",");

        // Current period data → _Sista_30_Dagar fields
        json.append("\"Publiseringar_Sista_30_Dagar\":").append(rs.getInt("publiseringar_30d")).append(",");
        json.append("\"Skapade_Guider_Sista_30_Dagar\":").append(rs.getInt("skapade_guider_30d")).append(",");
        json.append("\"Visningar_Sista_30_Dagar\":").append(rs.getInt("visningar_30d")).append(",");
        json.append("\"Processvisningar_Sista_30_Dagar\":").append(rs.getInt("processvisningar_30d")).append(",");
        json.append("\"Processer_Skapade_Sista_30_Dagar\":").append(rs.getInt("processer_skapade_30d")).append(",");

        // Previous period data → _30_60_Dagar fields
        json.append("\"Publiseringar_30_60_Dagar\":").append(rs.getInt("prev_publiseringar")).append(",");
        json.append("\"Skapade_Guider_30_60_Dagar\":").append(rs.getInt("prev_guider")).append(",");
        json.append("\"Visningar_30_60_Dagar\":").append(rs.getInt("prev_visningar")).append(",");
        json.append("\"Processvisningar_30_60_Dagar\":").append(rs.getInt("prev_processvisningar")).append(",");
        json.append("\"Processer_Skapade_30_60_Dagar\":").append(rs.getInt("prev_processer")).append(",");

        // New login/publishing metrics (current period, SUMmed)
        json.append("\"Antal_Inloggningar_Prod_Admin\":").append(rs.getInt("antal_inloggningar_prod_admin")).append(",");
        json.append("\"Antal_Unika_Inloggade_Prod_Admin\":").append(rs.getInt("antal_unika_inloggade_prod_admin")).append(",");
        json.append("\"Antal_Unika_Publicerande_Prod\":").append(rs.getInt("antal_unika_publicerande_prod")).append(",");

        // Previous period login/publishing metrics
        json.append("\"Inloggningar_Prod_Admin_Prev\":").append(rs.getInt("prev_inloggningar")).append(",");
        json.append("\"Unika_Inloggade_Prod_Admin_Prev\":").append(rs.getInt("prev_unika_inloggade")).append(",");
        json.append("\"Unika_Publicerande_Prod_Prev\":").append(rs.getInt("prev_unika_publicerande")).append(",");

        // Static counts (from current snapshot)
        json.append("\"Antal_Producenter\":").append(rs.getInt("antal_producenter")).append(",");
        json.append("\"Antal_Administratorer\":").append(rs.getInt("antal_administratorer")).append(",");
        json.append("\"Totalt_Antal_Anvandare\":").append(rs.getInt("totalt_antal_anvandare")).append(",");
        json.append("\"Antal_Aktiva_Producenter_Sista_6_Manader\":").append(rs.getInt("antal_aktiva_producenter_6m"));

        json.append("}");
    }

    /**
     * GET /api/customer-stats/history?url=X&from=Y&to=Z&limit=156
     *
     * Returns weekly snapshots for a specific customer, ordered by date descending.
     * Each snapshot covers a 7-day window. Default limit: 156 (~3 years of weekly data).
     */
    private void handleHistory(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String urlParam = req.getParameter("url");
        if (urlParam == null || urlParam.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing 'url' parameter\"}");
            return;
        }

        String normalizedUrl = normalizeUrl(urlParam);
        String fromParam = req.getParameter("from");
        String toParam = req.getParameter("to");
        String limitParam = req.getParameter("limit");

        LocalDate fromDate = fromParam != null ? LocalDate.parse(fromParam) : LocalDate.now().minusYears(3);
        LocalDate toDate = toParam != null ? LocalDate.parse(toParam) : LocalDate.now();
        int limit = 156; // ~3 years of weekly snapshots
        if (limitParam != null) {
            try { limit = Integer.parseInt(limitParam); } catch (NumberFormatException ignored) {}
        }

        String sql =
            "SELECT csd.snapshot_date, csd.publiseringar_30d, csd.skapade_guider_30d, " +
            "  csd.visningar_30d, csd.processvisningar_30d, csd.processer_skapade_30d, " +
            "  csd.antal_producenter, csd.antal_administratorer, csd.totalt_antal_anvandare, " +
            "  csd.antal_aktiva_producenter_6m, " +
            "  csd.antal_inloggningar_prod_admin, csd.antal_unika_inloggade_prod_admin, " +
            "  csd.antal_unika_publicerande_prod, csd.version " +
            "FROM customer_stats_daily csd " +
            "JOIN servers s ON s.id = csd.server_id " +
            "WHERE s.url_normalized = ? " +
            "  AND csd.snapshot_date BETWEEN ? AND ? " +
            "ORDER BY csd.snapshot_date DESC " +
            "LIMIT ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, normalizedUrl);
            ps.setDate(2, Date.valueOf(fromDate));
            ps.setDate(3, Date.valueOf(toDate));
            ps.setInt(4, limit);

            StringBuilder json = new StringBuilder("[");
            try (ResultSet rs = ps.executeQuery()) {
            boolean first = true;

            while (rs.next()) {
                if (!first) json.append(",");
                first = false;

                json.append("{");
                json.append("\"snapshotDate\":").append(JsonUtil.quote(rs.getDate("snapshot_date").toString())).append(",");
                json.append("\"publiseringar\":").append(rs.getInt("publiseringar_30d")).append(",");
                json.append("\"guider\":").append(rs.getInt("skapade_guider_30d")).append(",");
                json.append("\"visningar\":").append(rs.getInt("visningar_30d")).append(",");
                json.append("\"processvisningar\":").append(rs.getInt("processvisningar_30d")).append(",");
                json.append("\"processer\":").append(rs.getInt("processer_skapade_30d")).append(",");
                json.append("\"producenter\":").append(rs.getInt("antal_producenter")).append(",");
                json.append("\"administratorer\":").append(rs.getInt("antal_administratorer")).append(",");
                json.append("\"anvandare\":").append(rs.getInt("totalt_antal_anvandare")).append(",");
                json.append("\"aktivaProducenter\":").append(rs.getInt("antal_aktiva_producenter_6m")).append(",");
                json.append("\"inloggningarProdAdmin\":").append(rs.getInt("antal_inloggningar_prod_admin")).append(",");
                json.append("\"unikaInloggadeProdAdmin\":").append(rs.getInt("antal_unika_inloggade_prod_admin")).append(",");
                json.append("\"unikaPublicerandeProd\":").append(rs.getInt("antal_unika_publicerande_prod")).append(",");
                json.append("\"version\":").append(JsonUtil.quote(rs.getString("version")));
                json.append("}");
            }

            json.append("]");
            } // close try(ResultSet)
            PrintWriter out = resp.getWriter();
            out.write(json.toString());
            out.flush();

        } catch (SQLException e) {
            log.error("Failed to load customer stats history for url={}", urlParam, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    /**
     * POST /api/customer-stats/import
     *
     * Accepts a JSON array matching allstats.json format.
     * Authenticated via X-API-Key header (checked in AuthFilter).
     * Upserts customers and inserts daily snapshots.
     */
    private void handleImport(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        resp.setContentType("application/json; charset=UTF-8");

        // Read request body
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }

        String jsonBody = body.toString().trim();
        if (!jsonBody.startsWith("[")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Expected JSON array\"}");
            return;
        }

        // Simple JSON array parsing - extract objects between { and }
        // This is intentionally basic; for production use, consider a JSON library
        int imported = 0;
        int errors = 0;

        String upsertServerSql =
            "INSERT INTO servers (url, url_normalized, current_version, first_seen, last_seen) " +
            "VALUES (?, ?, ?, CURDATE(), CURDATE()) " +
            "ON DUPLICATE KEY UPDATE current_version = VALUES(current_version), " +
            "last_seen = CURDATE(), url = VALUES(url)";

        String getServerIdSql = "SELECT id FROM servers WHERE url_normalized = ?";

        String insertStatsSql =
            "INSERT INTO customer_stats_daily (" +
            "  server_id, snapshot_date, " +
            "  publiseringar_30d, publiseringar_30_60d, " +
            "  skapade_guider_30d, skapade_guider_30_60d, " +
            "  visningar_30d, visningar_30_60d, " +
            "  processvisningar_30d, processvisningar_30_60d, " +
            "  processer_skapade_30d, processer_skapade_30_60d, " +
            "  antal_producenter, antal_administratorer, " +
            "  totalt_antal_anvandare, antal_aktiva_producenter_6m, " +
            "  antal_inloggningar_prod_admin, antal_unika_inloggade_prod_admin, " +
            "  antal_unika_publicerande_prod, version" +
            ") VALUES (?, CURDATE(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE " +
            "  publiseringar_30d = VALUES(publiseringar_30d), " +
            "  publiseringar_30_60d = VALUES(publiseringar_30_60d), " +
            "  skapade_guider_30d = VALUES(skapade_guider_30d), " +
            "  skapade_guider_30_60d = VALUES(skapade_guider_30_60d), " +
            "  visningar_30d = VALUES(visningar_30d), " +
            "  visningar_30_60d = VALUES(visningar_30_60d), " +
            "  processvisningar_30d = VALUES(processvisningar_30d), " +
            "  processvisningar_30_60d = VALUES(processvisningar_30_60d), " +
            "  processer_skapade_30d = VALUES(processer_skapade_30d), " +
            "  processer_skapade_30_60d = VALUES(processer_skapade_30_60d), " +
            "  antal_producenter = VALUES(antal_producenter), " +
            "  antal_administratorer = VALUES(antal_administratorer), " +
            "  totalt_antal_anvandare = VALUES(totalt_antal_anvandare), " +
            "  antal_aktiva_producenter_6m = VALUES(antal_aktiva_producenter_6m), " +
            "  antal_inloggningar_prod_admin = VALUES(antal_inloggningar_prod_admin), " +
            "  antal_unika_inloggade_prod_admin = VALUES(antal_unika_inloggade_prod_admin), " +
            "  antal_unika_publicerande_prod = VALUES(antal_unika_publicerande_prod), " +
            "  version = VALUES(version)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement psUpsert = conn.prepareStatement(upsertServerSql);
             PreparedStatement psGetId = conn.prepareStatement(getServerIdSql);
             PreparedStatement psInsert = conn.prepareStatement(insertStatsSql)) {

            // Parse each JSON object from the array
            // Simple approach: split by },{ pattern
            String inner = jsonBody.substring(1, jsonBody.length() - 1).trim();
            if (inner.isEmpty()) {
                resp.getWriter().write("{\"imported\":0,\"errors\":0}");
                return;
            }

            // Split objects - find matching braces
            int depth = 0;
            int start = -1;
            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);
                if (c == '{') {
                    if (depth == 0) start = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        String objStr = inner.substring(start, i + 1);
                        try {
                            importSingleRecord(objStr, psUpsert, psGetId, psInsert);
                            imported++;
                        } catch (Exception e) {
                            errors++;
                            log.error("Failed to import single stats record", e);
                        }
                        start = -1;
                    }
                }
            }

            resp.getWriter().write("{\"imported\":" + imported + ",\"errors\":" + errors + "}");

        } catch (SQLException e) {
            log.error("Failed to import customer stats batch", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    /**
     * Import a single server record from a JSON object string.
     * Upserts into servers table and inserts daily stats snapshot.
     */
    private void importSingleRecord(String jsonObj, PreparedStatement psUpsert,
                                     PreparedStatement psGetId, PreparedStatement psInsert)
            throws SQLException {

        String url = JsonUtil.extractJsonString(jsonObj, "url");
        if (url == null || url.isEmpty()) return;

        String normalizedUrl = normalizeUrl(url);
        String version = JsonUtil.extractJsonString(jsonObj, "Version");

        // Upsert server
        psUpsert.setString(1, url);
        psUpsert.setString(2, normalizedUrl);
        psUpsert.setString(3, version);
        psUpsert.executeUpdate();

        // Get server ID
        psGetId.setString(1, normalizedUrl);
        int serverId;
        try (ResultSet rs = psGetId.executeQuery()) {
            if (!rs.next()) return;
            serverId = rs.getInt(1);
        }

        // Insert stats
        psInsert.setInt(1, serverId);
        psInsert.setInt(2, JsonUtil.extractJsonInt(jsonObj, "Publiseringar_Sista_30_Dagar"));
        psInsert.setInt(3, JsonUtil.extractJsonInt(jsonObj, "Publiseringar_30_60_Dagar"));
        psInsert.setInt(4, JsonUtil.extractJsonInt(jsonObj, "Skapade_Guider_Sista_30_Dagar"));
        psInsert.setInt(5, JsonUtil.extractJsonInt(jsonObj, "Skapade_Guider_30_60_Dagar"));
        psInsert.setInt(6, JsonUtil.extractJsonInt(jsonObj, "Visningar_Sista_30_Dagar"));
        psInsert.setInt(7, JsonUtil.extractJsonInt(jsonObj, "Visningar_30_60_Dagar"));
        psInsert.setInt(8, JsonUtil.extractJsonInt(jsonObj, "Processvisningar_Sista_30_Dagar"));
        psInsert.setInt(9, JsonUtil.extractJsonInt(jsonObj, "Processvisningar_30_60_Dagar"));
        psInsert.setInt(10, JsonUtil.extractJsonInt(jsonObj, "Processer_Skapade_Sista_30_Dagar"));
        psInsert.setInt(11, JsonUtil.extractJsonInt(jsonObj, "Processer_Skapade_30_60_Dagar"));
        psInsert.setInt(12, JsonUtil.extractJsonInt(jsonObj, "Antal_Producenter"));
        psInsert.setInt(13, JsonUtil.extractJsonInt(jsonObj, "Antal_Administratorer"));
        psInsert.setInt(14, JsonUtil.extractJsonInt(jsonObj, "Totalt_Antal_Anvandare"));
        psInsert.setInt(15, JsonUtil.extractJsonInt(jsonObj, "Antal_Aktiva_Producenter_Sista_6_Manader"));
        psInsert.setInt(16, JsonUtil.extractJsonInt(jsonObj, "Antal_Inloggningar_Prod_Admin"));
        psInsert.setInt(17, JsonUtil.extractJsonInt(jsonObj, "Antal_Unika_Inloggade_Prod_Admin"));
        psInsert.setInt(18, JsonUtil.extractJsonInt(jsonObj, "Antal_Unika_Publicerande_Prod"));
        psInsert.setString(19, version);
        psInsert.executeUpdate();
    }

    /**
     * GET /api/customer-stats/servers — List all servers with exclusion status.
     * Admin-only.
     */
    private void handleListServers(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        resp.setContentType("application/json; charset=UTF-8");
        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT id, url, url_normalized, current_version, is_active, is_excluded, " +
                 "first_seen, last_seen FROM servers ORDER BY url_normalized")) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append("{");
                json.append("\"id\":").append(rs.getInt("id")).append(",");
                json.append("\"url\":").append(JsonUtil.quote(rs.getString("url"))).append(",");
                json.append("\"urlNormalized\":").append(JsonUtil.quote(rs.getString("url_normalized"))).append(",");
                json.append("\"version\":").append(JsonUtil.quote(rs.getString("current_version"))).append(",");
                json.append("\"isActive\":").append(rs.getBoolean("is_active")).append(",");
                json.append("\"isExcluded\":").append(rs.getBoolean("is_excluded")).append(",");
                json.append("\"firstSeen\":").append(JsonUtil.quote(rs.getString("first_seen"))).append(",");
                json.append("\"lastSeen\":").append(JsonUtil.quote(rs.getString("last_seen")));
                json.append("}");
            }
            json.append("]");
            resp.getWriter().write(json.toString());

        } catch (SQLException e) {
            log.error("Failed to list servers", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    /**
     * POST /api/customer-stats/servers/exclude — Toggle server exclusion.
     * Admin-only. Body: {"serverId":N,"excluded":true/false}
     */
    private void handleToggleExclude(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        resp.setContentType("application/json; charset=UTF-8");

        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        String jsonBody = body.toString();

        int serverId = JsonUtil.extractJsonInt(jsonBody, "serverId");
        // Extract boolean: look for "excluded":true or "excluded":false
        boolean excluded = jsonBody.matches("(?s).*\"excluded\"\\s*:\\s*true.*");

        if (serverId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing serverId\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE servers SET is_excluded = ? WHERE id = ?")) {
            ps.setBoolean(1, excluded);
            ps.setInt(2, serverId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                log.info("Server {} exclusion set to {} by admin {}", serverId, excluded, admin.getEmail());
                resp.getWriter().write("{\"ok\":true}");
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"Server not found\"}");
            }
        } catch (SQLException e) {
            log.error("Failed to toggle server exclusion", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    /**
     * Normalize a URL to a comparable domain string.
     * Matches the JavaScript normalizeUrl() in displaystats.html.
     */
    private static String normalizeUrl(String url) {
        if (url == null) return "";
        return url.toLowerCase()
                .replaceAll("^https?://", "")
                .replaceAll("/.*$", "")
                .replaceAll("/$", "");
    }

}
