package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.util.AppVersion;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.JsonUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

/**
 * Version API Servlet.
 *
 * GET /api/version — returns application and database version info.
 *
 * Also creates/seeds schema_version table on startup.
 */
public class VersionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(VersionServlet.class);

    @Override
    public void init() throws ServletException {
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {
            // Create schema_version table if not exists
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS schema_version (" +
                "  version       VARCHAR(20)  NOT NULL PRIMARY KEY," +
                "  description   VARCHAR(255) NOT NULL," +
                "  applied_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP," +
                "  applied_by    VARCHAR(100) NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            // Seed baseline if table is empty
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version");
            rs.next();
            if (rs.getInt(1) == 0) {
                stmt.executeUpdate(
                    "INSERT INTO schema_version (version, description) " +
                    "VALUES ('1.0.0', 'Baseline - alla befintliga tabeller och moduler')"
                );
                log.info("schema_version seeded with v1.0.0 baseline");
            }

            log.info("schema_version table verified — app version {}", AppVersion.VERSION);
        } catch (SQLException e) {
            log.warn("Could not init schema_version: {}", e.getMessage());
        }
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

        String dbVersion = "unknown";
        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT version FROM schema_version ORDER BY applied_at DESC LIMIT 1")) {
            if (rs.next()) {
                dbVersion = rs.getString("version");
            }
        } catch (SQLException e) {
            log.warn("Could not read schema_version: {}", e.getMessage());
        }

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"version\":").append(JsonUtil.quote(AppVersion.VERSION)).append(",");
        json.append("\"dbVersion\":").append(JsonUtil.quote(dbVersion)).append(",");
        json.append("\"buildDate\":").append(JsonUtil.quote(AppVersion.BUILD_DATE));
        json.append("}");

        PrintWriter out = resp.getWriter();
        out.write(json.toString());
        out.flush();
    }
}
