package com.infocaption.dashboard.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application configuration utility.
 * Loads key-value config from the app_config table with in-memory caching.
 *
 * Bootstrap issue: DBUtil needs db.url/db.user/db.password from app_config,
 * but app_config is in the database. Solution: DBUtil keeps hardcoded fallback
 * values and AppConfig bootstraps from DBUtil first, then subsequent calls
 * use the cached values. Other classes (SmartassicDBUtil, AcsEmailUtil, etc.)
 * fully rely on AppConfig.
 *
 * Cache is refreshed every 5 minutes or on demand via reload().
 */
public class AppConfig {
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private static final Map<String, String> cache = new ConcurrentHashMap<>();
    private static volatile long lastLoadTime = 0;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes
    private static volatile boolean loaded = false;
    private static volatile boolean loading = false; // re-entrancy guard for bootstrap

    /**
     * Get a config value by key, with fallback default.
     */
    public static String get(String key, String defaultValue) {
        ensureLoaded();
        String val = cache.get(key);
        return val != null ? val : defaultValue;
    }

    /**
     * Get a config value by key (returns null if not found).
     */
    public static String get(String key) {
        return get(key, null);
    }

    /**
     * Get an integer config value with fallback.
     */
    public static int getInt(String key, int defaultValue) {
        String val = get(key, null);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get a long config value with fallback.
     */
    public static long getLong(String key, long defaultValue) {
        String val = get(key, null);
        if (val == null) return defaultValue;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get a boolean config value with fallback.
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String val = get(key, null);
        if (val == null) return defaultValue;
        return "true".equalsIgnoreCase(val.trim()) || "1".equals(val.trim());
    }

    /**
     * Force reload the config cache from the database.
     */
    public static void reload() {
        loading = true;
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT config_key, config_value FROM app_config");
             ResultSet rs = ps.executeQuery()) {

            Map<String, String> newCache = new ConcurrentHashMap<>();
            while (rs.next()) {
                String key = rs.getString("config_key");
                String value = rs.getString("config_value");
                if (key != null) {
                    newCache.put(key, value != null ? value : "");
                }
            }
            // Atomic swap: remove stale keys, then put new — avoids empty cache window
            // where concurrent get() would return null/defaults
            cache.keySet().retainAll(newCache.keySet());
            cache.putAll(newCache);
            lastLoadTime = System.currentTimeMillis();
            loaded = true;

        } catch (SQLException e) {
            // If we can't load from DB (first startup, table missing, etc.),
            // mark as loaded so we don't retry on every call — use defaults.
            log.warn("Could not load config from database: {}", e.getMessage());
            loaded = true;
            lastLoadTime = System.currentTimeMillis();
        } finally {
            loading = false;
        }
    }

    /**
     * Ensure config has been loaded (lazy initialization with TTL refresh).
     *
     * Bootstrap guard: When reload() calls DBUtil.getConnection(), which calls
     * AppConfig.get(), we must not recursively call reload() again.
     * The 'loading' flag prevents this — during bootstrap, get() returns the
     * fallback defaults (cache is empty, so defaultValue is used).
     */
    private static void ensureLoaded() {
        if (loading) return; // re-entrancy guard: DBUtil calling AppConfig during bootstrap
        if (!loaded || (System.currentTimeMillis() - lastLoadTime > CACHE_TTL_MS)) {
            synchronized (AppConfig.class) {
                if (loading) return;
                if (!loaded || (System.currentTimeMillis() - lastLoadTime > CACHE_TTL_MS)) {
                    reload();
                }
            }
        }
    }

    /**
     * Update a single config value in the database and cache.
     * Used by the admin API.
     */
    public static void set(String key, String value, int updatedByUserId) throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE app_config SET config_value = ?, updated_by = ?, updated_at = NOW() WHERE config_key = ?")) {
            ps.setString(1, value);
            ps.setInt(2, updatedByUserId);
            ps.setString(3, key);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                cache.put(key, value != null ? value : "");
            }
        }
    }

    /**
     * Set a config value without a user context (system-level updates).
     * Uses INSERT ON DUPLICATE KEY UPDATE so the key is created if it doesn't exist.
     * Used by CryptoUtil for auto-generating the master key.
     */
    public static void set(String key, String value) {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO app_config (config_key, config_value, category, is_secret) " +
                 "VALUES (?, ?, 'security', 1) " +
                 "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), updated_at = NOW()")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
            cache.put(key, value != null ? value : "");
        } catch (SQLException e) {
            log.warn("Could not set config key '{}': {}", key, e.getMessage());
        }
    }

    /**
     * Get all config entries as a list (for admin display).
     * Returns JSON-style representation for the admin API to consume.
     */
    public static Map<String, String> getAll() {
        ensureLoaded();
        return new ConcurrentHashMap<>(cache);
    }
}
