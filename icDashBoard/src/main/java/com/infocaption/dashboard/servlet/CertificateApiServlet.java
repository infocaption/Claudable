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
 * Certificate API Servlet.
 *
 * GET /api/certificates — List all certificates with expiry status
 *
 * Status classification:
 *   expired  = days until expiry <= 0
 *   critical = 1-30 days
 *   warning  = 31-60 days
 *   ok       = > 60 days
 */
public class CertificateApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(CertificateApiServlet.class);

    @Override
    public void init() throws ServletException {
        // Auto-create certificates table if it doesn't exist (idempotent)
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS certificates (" +
                "  id                INT AUTO_INCREMENT PRIMARY KEY," +
                "  server_ip         VARCHAR(50)  NOT NULL," +
                "  keystore_path     VARCHAR(500) NOT NULL," +
                "  hostname_pattern  VARCHAR(255) NULL," +
                "  subject           VARCHAR(500) NULL," +
                "  issuer            VARCHAR(500) NULL," +
                "  serial_number     VARCHAR(100) NULL," +
                "  valid_from        DATE         NOT NULL," +
                "  valid_to          DATE         NOT NULL," +
                "  last_checked      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  UNIQUE INDEX idx_cert_server_keystore (server_ip, keystore_path(255))" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            log("Certificates table verified/created successfully");
        } catch (SQLException e) {
            log("Warning: Could not auto-create certificates table: " + e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Auth check
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write("{\"error\":\"Not authenticated\"}");
            return;
        }

        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        handleListCertificates(req, resp);
    }

    /**
     * GET /api/certificates
     *
     * Returns all certificates with computed expiry status.
     */
    private void handleListCertificates(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String sql =
            "SELECT id, server_ip, keystore_path, hostname_pattern, " +
            "  subject, issuer, serial_number, " +
            "  valid_from, valid_to, last_checked, " +
            "  DATEDIFF(valid_to, CURDATE()) AS days_until_expiry " +
            "FROM certificates " +
            "ORDER BY DATEDIFF(valid_to, CURDATE()) ASC, server_ip ASC";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ResultSet rs = ps.executeQuery();
            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            while (rs.next()) {
                if (!first) json.append(",");
                first = false;

                int daysUntilExpiry = rs.getInt("days_until_expiry");
                String status;
                if (daysUntilExpiry <= 0) {
                    status = "expired";
                } else if (daysUntilExpiry <= 30) {
                    status = "critical";
                } else if (daysUntilExpiry <= 60) {
                    status = "warning";
                } else {
                    status = "ok";
                }

                Date validFrom = rs.getDate("valid_from");
                Date validTo = rs.getDate("valid_to");
                Timestamp lastChecked = rs.getTimestamp("last_checked");

                json.append("{");
                json.append("\"id\":").append(rs.getInt("id")).append(",");
                json.append("\"serverIp\":").append(JsonUtil.quote(rs.getString("server_ip"))).append(",");
                json.append("\"keystorePath\":").append(JsonUtil.quote(rs.getString("keystore_path"))).append(",");
                json.append("\"hostnamePattern\":").append(JsonUtil.quote(rs.getString("hostname_pattern"))).append(",");
                json.append("\"subject\":").append(JsonUtil.quote(rs.getString("subject"))).append(",");
                json.append("\"issuer\":").append(JsonUtil.quote(rs.getString("issuer"))).append(",");
                json.append("\"serialNumber\":").append(JsonUtil.quote(rs.getString("serial_number"))).append(",");
                json.append("\"validFrom\":").append(JsonUtil.quote(validFrom != null ? validFrom.toString() : null)).append(",");
                json.append("\"validTo\":").append(JsonUtil.quote(validTo != null ? validTo.toString() : null)).append(",");
                json.append("\"daysUntilExpiry\":").append(daysUntilExpiry).append(",");
                json.append("\"status\":").append(JsonUtil.quote(status)).append(",");
                json.append("\"lastChecked\":").append(JsonUtil.quote(lastChecked != null ? lastChecked.toString() : null));
                json.append("}");
            }

            json.append("]");
            PrintWriter out = resp.getWriter();
            out.write(json.toString());
            out.flush();

        } catch (SQLException e) {
            log.error("Failed to list certificates", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }
}
