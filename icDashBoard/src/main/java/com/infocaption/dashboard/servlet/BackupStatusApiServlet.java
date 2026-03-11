package com.infocaption.dashboard.servlet;

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
 * Backup Status API Servlet.
 *
 * GET /api/backups — List all backup file statuses
 *
 * Status classification (set by check-backups.ps1):
 *   critical = file missing, size < 500 MB, or older than 2 days
 *   warning  = size < 1000 MB, or older than 1 day
 *   ok       = file exists, large enough, recent enough
 */
public class BackupStatusApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(BackupStatusApiServlet.class);

    @Override
    public void init() throws ServletException {
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS backup_status (" +
                "  id              INT AUTO_INCREMENT PRIMARY KEY," +
                "  server_ip       VARCHAR(50)   NOT NULL," +
                "  file_path       VARCHAR(500)  NOT NULL," +
                "  file_name       VARCHAR(255)  NOT NULL," +
                "  file_size_mb    DECIMAL(10,2) NULL," +
                "  last_modified   DATETIME      NULL," +
                "  file_exists     TINYINT(1)    NOT NULL DEFAULT 0," +
                "  database_count  INT           NULL," +
                "  status          ENUM('ok','warning','critical') NOT NULL DEFAULT 'critical'," +
                "  status_reason   VARCHAR(255)  NULL," +
                "  last_scanned    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  UNIQUE INDEX idx_backup_server_file (server_ip, file_path(255))" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            log.info("backup_status table verified/created successfully");
        } catch (SQLException e) {
            log.warn("Could not auto-create backup_status table: {}", e.getMessage());
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

        handleListBackups(req, resp);
    }

    private void handleListBackups(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String sql =
            "SELECT id, server_ip, file_path, file_name, " +
            "  file_size_mb, last_modified, file_exists, database_count, " +
            "  status, status_reason, last_scanned, " +
            "  TIMESTAMPDIFF(HOUR, last_modified, NOW()) AS hours_since_modified, " +
            "  TIMESTAMPDIFF(HOUR, last_scanned, NOW()) AS hours_since_scanned " +
            "FROM backup_status " +
            "ORDER BY FIELD(status, 'critical', 'warning', 'ok'), server_ip ASC, file_name ASC";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            while (rs.next()) {
                if (!first) json.append(",");
                first = false;

                Timestamp lastModified = rs.getTimestamp("last_modified");
                Timestamp lastScanned = rs.getTimestamp("last_scanned");

                json.append("{");
                json.append("\"id\":").append(rs.getInt("id")).append(",");
                json.append("\"serverIp\":").append(JsonUtil.quote(rs.getString("server_ip"))).append(",");
                json.append("\"filePath\":").append(JsonUtil.quote(rs.getString("file_path"))).append(",");
                json.append("\"fileName\":").append(JsonUtil.quote(rs.getString("file_name"))).append(",");

                double sizeMb = rs.getDouble("file_size_mb");
                if (rs.wasNull()) {
                    json.append("\"fileSizeMb\":null,");
                } else {
                    json.append("\"fileSizeMb\":").append(sizeMb).append(",");
                }

                json.append("\"lastModified\":").append(JsonUtil.quote(lastModified != null ? lastModified.toString() : null)).append(",");
                json.append("\"fileExists\":").append(rs.getBoolean("file_exists")).append(",");

                int dbCount = rs.getInt("database_count");
                json.append("\"databaseCount\":").append(rs.wasNull() ? "null" : String.valueOf(dbCount)).append(",");
                json.append("\"status\":").append(JsonUtil.quote(rs.getString("status"))).append(",");
                json.append("\"statusReason\":").append(JsonUtil.quote(rs.getString("status_reason"))).append(",");
                json.append("\"lastScanned\":").append(JsonUtil.quote(lastScanned != null ? lastScanned.toString() : null)).append(",");

                int hoursSinceModified = rs.getInt("hours_since_modified");
                json.append("\"hoursSinceModified\":").append(rs.wasNull() ? "null" : String.valueOf(hoursSinceModified)).append(",");

                int hoursSinceScanned = rs.getInt("hours_since_scanned");
                json.append("\"hoursSinceScanned\":").append(rs.wasNull() ? "null" : String.valueOf(hoursSinceScanned));

                json.append("}");
            }

            json.append("]");
            PrintWriter out = resp.getWriter();
            out.write(json.toString());
            out.flush();

        } catch (SQLException e) {
            log.error("Failed to list backup statuses", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }
}
