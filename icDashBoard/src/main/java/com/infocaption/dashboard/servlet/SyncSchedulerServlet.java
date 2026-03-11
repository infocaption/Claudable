package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.SyncExecutor;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import java.sql.*;
import java.util.concurrent.*;

/**
 * Background scheduler for sync configs with schedule_minutes > 0.
 * Checks every minute for due configs and runs them via SyncExecutor.
 */
public class SyncSchedulerServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private ScheduledExecutorService scheduler;

    /** Track which configs are currently running to prevent overlap */
    private final ConcurrentHashMap<Integer, Boolean> runningConfigs = new ConcurrentHashMap<>();

    @Override
    public void init() throws ServletException {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SyncScheduler");
            t.setDaemon(true);
            return t;
        });

        // Check for due syncs every minute, initial delay 1 minute
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkAndRunDueSyncs();
            } catch (Exception e) {
                log("Sync scheduler error: " + e.getMessage());
            }
        }, 1, 1, TimeUnit.MINUTES);

        log("SyncSchedulerServlet initialized — checking every minute for due syncs");
    }

    /**
     * Find active configs whose schedule is due and run them.
     */
    private void checkAndRunDueSyncs() {
        String sql =
            "SELECT id, name FROM sync_configs " +
            "WHERE is_active = 1 AND schedule_minutes > 0 " +
            "AND (last_run_at IS NULL " +
            "     OR TIMESTAMPDIFF(MINUTE, last_run_at, NOW()) >= schedule_minutes)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int configId = rs.getInt("id");
                String configName = rs.getString("name");

                // Skip if already running
                if (runningConfigs.putIfAbsent(configId, Boolean.TRUE) != null) {
                    log("Sync '" + configName + "' (id=" + configId + ") still running, skipping");
                    continue;
                }

                try {
                    log("Running scheduled sync: " + configName + " (id=" + configId + ")");
                    SyncExecutor.execute(configId, null); // null = scheduled, not manual
                    log("Completed scheduled sync: " + configName);
                } catch (Exception e) {
                    log("Failed scheduled sync '" + configName + "': " + e.getMessage());
                } finally {
                    runningConfigs.remove(configId);
                }
            }

        } catch (SQLException e) {
            log("Error checking due syncs: " + e.getMessage());
        }
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            log("Sync scheduler stopped");
        }
    }
}
