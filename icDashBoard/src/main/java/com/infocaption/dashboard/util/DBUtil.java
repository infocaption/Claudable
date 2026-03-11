package com.infocaption.dashboard.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Database connection utility for the local icdashboard database.
 *
 * Credential resolution order:
 *   1. app_config table (via AppConfig — runtime-editable in Admin panel)
 *   2. WEB-INF/app-secrets.properties (via SecretsConfig — loaded at startup)
 *   3. Empty defaults (connection will fail with clear error)
 *
 * SecretsConfig is initialized by AppStartupListener before any servlets.
 * AppConfig is checked on every getConnection() call (with 5-min cache).
 */
public class DBUtil {

    private static final Logger log = LoggerFactory.getLogger(DBUtil.class);

    // Simple connection pool
    private static final int POOL_SIZE = 10;
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
        String url = AppConfig.get("db.url", SecretsConfig.get("db.url", ""));
        String user = AppConfig.get("db.user", SecretsConfig.get("db.user", ""));
        String password = AppConfig.get("db.password", SecretsConfig.get("db.password", ""));

        if (url.isEmpty()) {
            throw new SQLException("Database URL not configured. Check app-secrets.properties or Admin > Installningar.");
        }

        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Return a connection to the pool instead of closing it.
     * Usage: DBUtil.returnConnection(conn) in finally blocks,
     * or use getConnection() with try-with-resources (auto-closes).
     */
    public static void returnConnection(Connection conn) {
        if (conn == null) return;
        try {
            if (!conn.isClosed() && conn.isValid(1)) {
                if (!pool.offer(conn)) {
                    conn.close(); // Pool full, close the connection
                }
                return;
            }
        } catch (SQLException e) {
            // Fall through to close
        }
        try { conn.close(); } catch (SQLException ignored) {}
    }
}
