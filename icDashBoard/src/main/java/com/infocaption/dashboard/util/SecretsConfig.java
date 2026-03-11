package com.infocaption.dashboard.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import java.io.InputStream;
import java.util.Properties;

/**
 * Centralized loader for sensitive configuration from WEB-INF/app-secrets.properties.
 *
 * Loaded once at startup. All secrets should be read from this class instead of
 * being hardcoded as Java constants. Runtime overrides are still possible via
 * AppConfig (app_config DB table) — this class provides the bootstrap fallback.
 *
 * Usage:
 *   String value = SecretsConfig.get("db.password");           // returns null if missing
 *   String value = SecretsConfig.get("db.password", "");       // returns default if missing
 */
public class SecretsConfig {

    private static final Logger log = LoggerFactory.getLogger(SecretsConfig.class);

    private static final String SECRETS_FILE = "/WEB-INF/app-secrets.properties";
    private static final String LEGACY_DB_FILE = "/WEB-INF/db.properties";

    private static final Properties secrets = new Properties();
    private static volatile boolean loaded = false;

    /**
     * Initialize from ServletContext (call from a load-on-startup servlet).
     * Loads app-secrets.properties first, then db.properties as fallback
     * for backward compatibility.
     */
    public static synchronized void init(ServletContext ctx) {
        if (loaded) return;

        // Primary: app-secrets.properties
        try (InputStream is = ctx.getResourceAsStream(SECRETS_FILE)) {
            if (is != null) {
                secrets.load(is);
                log.info("Loaded {} secrets from {}", secrets.size(), SECRETS_FILE);
            } else {
                log.warn("{} not found — trying legacy {}", SECRETS_FILE, LEGACY_DB_FILE);
            }
        } catch (Exception e) {
            log.error("Failed to load {}: {}", SECRETS_FILE, e.getMessage());
        }

        // Fallback: legacy db.properties (for backward compatibility)
        if (secrets.isEmpty()) {
            try (InputStream is = ctx.getResourceAsStream(LEGACY_DB_FILE)) {
                if (is != null) {
                    secrets.load(is);
                    log.info("Loaded {} secrets from legacy {}", secrets.size(), LEGACY_DB_FILE);
                }
            } catch (Exception e) {
                log.warn("Failed to load legacy {}: {}", LEGACY_DB_FILE, e.getMessage());
            }
        }

        loaded = true;
    }

    /**
     * Get a secret value by key.
     * @return the value, or null if not found
     */
    public static String get(String key) {
        return secrets.getProperty(key);
    }

    /**
     * Get a secret value by key with a default fallback.
     * @return the value, or defaultValue if not found or empty
     */
    public static String get(String key, String defaultValue) {
        String val = secrets.getProperty(key);
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }

    /**
     * Check if the secrets have been loaded.
     */
    public static boolean isLoaded() {
        return loaded;
    }
}
