package com.infocaption.dashboard.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Database connection utility for the external SuperOffice/smartassic database.
 * Used by ContactListServlet to query contacts for email list building.
 *
 * Credential resolution order:
 *   1. app_config table (via AppConfig — runtime-editable in Admin panel)
 *   2. WEB-INF/app-secrets.properties (via SecretsConfig — loaded at startup)
 *   3. Empty defaults (connection will fail with clear error)
 *
 * Tables: zsocompany, zsocontacts, zsoserver
 */
public class SmartassicDBUtil {

    private static final Logger log = LoggerFactory.getLogger(SmartassicDBUtil.class);

    // Simple connection pool
    private static final int POOL_SIZE = 5;
    private static final BlockingQueue<Connection> pool = new ArrayBlockingQueue<>(POOL_SIZE);

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL JDBC driver not found", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        // Try to reuse a pooled connection
        Connection conn = pool.poll();
        if (conn != null) {
            try {
                if (!conn.isClosed() && conn.isValid(2)) {
                    return conn;
                }
                conn.close();
            } catch (SQLException e) {
                // Connection is stale, create a new one
            }
        }

        // Resolve credentials: AppConfig (DB) → SecretsConfig (properties file) → empty
        String url = AppConfig.get("db.smartassic.url", SecretsConfig.get("db.smartassic.url", ""));
        String user = AppConfig.get("db.smartassic.user", SecretsConfig.get("db.smartassic.user", ""));
        String password = AppConfig.get("db.smartassic.password", SecretsConfig.get("db.smartassic.password", ""));

        if (url.isEmpty()) {
            throw new SQLException("Smartassic DB URL not configured. Check app-secrets.properties or Admin > Installningar.");
        }

        return DriverManager.getConnection(url, user, password);
    }

    public static void returnConnection(Connection conn) {
        if (conn == null) return;
        try {
            if (!conn.isClosed() && conn.isValid(1)) {
                if (!pool.offer(conn)) {
                    conn.close();
                }
                return;
            }
        } catch (SQLException e) { /* fall through */ }
        try { conn.close(); } catch (SQLException ignored) {}
    }
}
