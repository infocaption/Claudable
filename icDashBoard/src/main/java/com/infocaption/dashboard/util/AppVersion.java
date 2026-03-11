package com.infocaption.dashboard.util;

/**
 * Application version constant.
 * Single source of truth for the product version.
 *
 * When releasing a new version:
 * 1. Update VERSION here
 * 2. Create sql/vX.Y_to_vX.Z.sql migration
 * 3. Run migration (inserts row in schema_version)
 * 4. Update install-package/install.sql
 */
public class AppVersion {
    public static final String VERSION = "1.0.0";
    public static final String BUILD_DATE = "2026-02-25";

    private AppVersion() {} // Utility class
}
