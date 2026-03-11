package com.infocaption.dashboard.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Centralized audit logging for security-relevant events.
 * Logs to both the audit_log DB table and SLF4J.
 */
public class AuditUtil {
    private static final Logger log = LoggerFactory.getLogger(AuditUtil.class);

    // Event types
    public static final String LOGIN_SUCCESS = "login_success";
    public static final String LOGIN_FAILED = "login_failed";
    public static final String LOGOUT = "logout";
    public static final String ADMIN_ACTION = "admin_action";
    public static final String CONFIG_CHANGE = "config_change";
    public static final String USER_DELETE = "user_delete";
    public static final String TOKEN_CREATE = "token_create";
    public static final String TOKEN_DELETE = "token_delete";
    public static final String SYNC_RUN = "sync_run";
    public static final String SYNC_CONFIG_CHANGE = "sync_config_change";
    public static final String GROUP_CHANGE = "group_change";
    public static final String MODULE_CHANGE = "module_change";

    /**
     * Log an audit event.
     */
    public static void logEvent(String eventType, Integer userId, String username,
                                 String ipAddress, String targetType, String targetId,
                                 String details) {
        // Always log to SLF4J
        log.info("AUDIT [{}] user={} ip={} target={}:{} - {}",
                eventType, userId != null ? userId : username, ipAddress,
                targetType, targetId, details);

        // Also log to database (async-safe, fire-and-forget)
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO audit_log (event_type, user_id, username, ip_address, target_type, target_id, details) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, eventType);
            if (userId != null) ps.setInt(2, userId); else ps.setNull(2, java.sql.Types.INTEGER);
            ps.setString(3, username);
            ps.setString(4, ipAddress);
            ps.setString(5, targetType);
            ps.setString(6, targetId);
            ps.setString(7, details != null && details.length() > 2000 ? details.substring(0, 2000) : details);
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("Failed to write audit log to DB: {}", e.getMessage());
        }

        // Periodic cleanup
        cleanupOldEntries();
    }

    /**
     * Convenience: log with HttpServletRequest for IP extraction.
     */
    public static void logEvent(String eventType, Integer userId, HttpServletRequest req,
                                 String targetType, String targetId, String details) {
        logEvent(eventType, userId, null, getClientIp(req), targetType, targetId, details);
    }

    /**
     * Log a failed login attempt (no user_id, just username).
     */
    public static void logFailedLogin(String username, HttpServletRequest req) {
        logEvent(LOGIN_FAILED, null, username, getClientIp(req), "auth", null,
                "Failed login attempt for: " + username);
    }

    private static String getClientIp(HttpServletRequest req) {
        if (req == null) return null;
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private static long lastCleanup = 0;
    private static final long CLEANUP_INTERVAL = 24 * 60 * 60 * 1000L; // 24 hours

    private static void cleanupOldEntries() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < CLEANUP_INTERVAL) return;
        lastCleanup = now;

        int retentionDays = AppConfig.getInt("audit.retentionDays", 90);
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM audit_log WHERE created_at < DATE_SUB(NOW(), INTERVAL ? DAY)")) {
            ps.setInt(1, retentionDays);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                log.info("Cleaned up {} old audit log entries (retention: {} days)", deleted, retentionDays);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup audit log: {}", e.getMessage());
        }
    }
}
