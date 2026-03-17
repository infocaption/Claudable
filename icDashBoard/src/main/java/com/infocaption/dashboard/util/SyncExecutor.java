package com.infocaption.dashboard.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core sync execution engine.
 * Fetches JSON from external REST APIs and upserts data into local DB tables.
 *
 * Used by SyncConfigServlet (manual run) and SyncSchedulerServlet (scheduled run).
 */
public class SyncExecutor {

    private static final Logger log = LoggerFactory.getLogger(SyncExecutor.class);

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Whitelist of tables that can be sync targets */
    private static final Set<String> ALLOWED_TABLES = new HashSet<>(Arrays.asList(
        "customers", "servers", "license_keys"
    ));

    /**
     * Validate that a URL does not point to internal/private network addresses (SSRF protection).
     * Blocks: localhost, 127.x.x.x, 10.x.x.x, 172.16-31.x.x, 192.168.x.x, 169.254.x.x, [::1], etc.
     * @throws IllegalArgumentException if the URL targets a private/internal address
     */
    public static void validateUrlNotInternal(String url) {
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                throw new IllegalArgumentException("URL has no host: " + url);
            }

            // Block obvious hostnames
            String lowerHost = host.toLowerCase();
            if (lowerHost.equals("localhost") || lowerHost.endsWith(".local")
                    || lowerHost.endsWith(".internal") || lowerHost.endsWith(".localhost")) {
                throw new IllegalArgumentException("URL targets a local/internal host: " + host);
            }

            // Resolve DNS and check IP
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isAnyLocalAddress()
                        || addr.isMulticastAddress()) {
                    throw new IllegalArgumentException(
                        "URL resolves to a private/internal IP: " + addr.getHostAddress());
                }
                // Block IPv4-mapped IPv6 for private ranges
                byte[] raw = addr.getAddress();
                if (raw.length == 16) {
                    // Check if it's an IPv4-mapped IPv6 (::ffff:x.x.x.x)
                    boolean mapped = true;
                    for (int i = 0; i < 10; i++) { if (raw[i] != 0) { mapped = false; break; } }
                    if (mapped && raw[10] == (byte) 0xff && raw[11] == (byte) 0xff) {
                        // Extract IPv4 portion and re-check
                        byte[] ipv4 = new byte[] { raw[12], raw[13], raw[14], raw[15] };
                        InetAddress v4 = InetAddress.getByAddress(ipv4);
                        if (v4.isLoopbackAddress() || v4.isLinkLocalAddress()
                                || v4.isSiteLocalAddress() || v4.isAnyLocalAddress()) {
                            throw new IllegalArgumentException(
                                "URL resolves to a private IPv4-mapped-IPv6 IP: " + addr.getHostAddress());
                        }
                    }
                }
                // Block metadata endpoints (169.254.169.254, fd00::, etc.)
                if (raw.length == 4) {
                    int b0 = raw[0] & 0xFF;
                    int b1 = raw[1] & 0xFF;
                    if (b0 == 169 && b1 == 254) {
                        throw new IllegalArgumentException(
                            "URL resolves to a link-local/metadata IP: " + addr.getHostAddress());
                    }
                    // Block 0.0.0.0/8
                    if (b0 == 0) {
                        throw new IllegalArgumentException(
                            "URL resolves to unspecified IP range: " + addr.getHostAddress());
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw e; // re-throw our own validation errors
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot resolve URL host for SSRF validation: " + e.getMessage());
        }
    }

    /**
     * Execute a sync config: fetch external data, upsert to local DB.
     * Returns the sync_run_history ID.
     */
    public static long execute(int configId, Integer triggeredByUserId) {
        long historyId = 0;

        try (Connection conn = DBUtil.getConnection()) {
            // 1. Load config
            SyncConfig config = loadConfig(conn, configId);
            if (config == null) {
                throw new RuntimeException("Sync config not found: " + configId);
            }

            // 2. Create history record
            historyId = createHistoryRecord(conn, configId, triggeredByUserId);

            // 3. Validate target table
            if (!ALLOWED_TABLES.contains(config.targetTable.toLowerCase())) {
                throw new RuntimeException("Table not allowed: " + config.targetTable);
            }

            // 4. Validate columns against INFORMATION_SCHEMA
            List<String[]> mappings = parseMappings(config.fieldMappings);
            List<String> targetColumns = new ArrayList<>();
            targetColumns.add(config.idFieldTarget);
            for (String[] m : mappings) {
                targetColumns.add(m[1]);
            }
            validateColumns(conn, config.targetTable, targetColumns);

            // Validate lookup table columns
            for (String[] m : mappings) {
                String lookup = m.length > 2 ? m[2] : null;
                if (lookup != null && !lookup.isEmpty() && lookup.contains(".")) {
                    String[] parts = lookup.split("\\.", 2);
                    validateColumns(conn, parts[0], Arrays.asList("id", parts[1]));
                }
            }

            // 5. SSRF validation — block internal/private IPs
            validateUrlNotInternal(config.sourceUrl);

            // 6. Fetch external data
            HttpRequest request = buildAuthenticatedRequest(
                config.sourceUrl, config.authType, config.authConfig);
            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("HTTP " + response.statusCode() + " from " + config.sourceUrl);
            }

            String jsonBody = response.body();

            // 6. Navigate to array via json_root_path
            String arrayJson = navigateJsonPath(jsonBody, config.jsonRootPath);

            // 7. Parse objects from array
            List<String> objects = extractJsonObjects(arrayJson);
            int fetched = objects.size();
            int upserted = 0;
            int failed = 0;

            // 8. Build and execute SQL for each object
            String sql;
            if (config.updateOnly) {
                sql = buildUpdateSql(config.targetTable, config.idFieldTarget, mappings);
            } else {
                sql = buildUpsertSql(config.targetTable, config.idFieldTarget, mappings);
            }

            log.info("SQL ({}): {}", config.updateOnly ? "UPDATE-ONLY" : "UPSERT", sql);

            // Parse field transforms (e.g., "serverUrl|url_normalize" → field + transform)
            String[] idParts = parseFieldTransform(config.idFieldSource);
            String idFieldName = idParts[0];
            String idTransform = idParts[1];

            String lastError = null;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (String obj : objects) {
                    try {
                        if (config.updateOnly) {
                            // UPDATE mode: SET col=? WHERE id_col=?
                            // Mapping values first, then ID value last (WHERE clause)
                            int paramIdx = 1;
                            for (int i = 0; i < mappings.size(); i++) {
                                String[] srcParts = parseFieldTransform(mappings.get(i)[0]);
                                String value = extractJsonFieldValue(obj, srcParts[0]);
                                value = applyTransform(value, srcParts[1]);
                                ps.setString(paramIdx++, value);
                            }
                            // ID value for WHERE clause
                            String idValue = extractJsonFieldValue(obj, idFieldName);
                            idValue = applyTransform(idValue, idTransform);
                            ps.setString(paramIdx, idValue);
                        } else {
                            // UPSERT mode: INSERT (id, cols...) VALUES (?, ?...)
                            // ID value first
                            String idValue = extractJsonFieldValue(obj, idFieldName);
                            idValue = applyTransform(idValue, idTransform);
                            ps.setString(1, idValue);
                            // Mapping values
                            for (int i = 0; i < mappings.size(); i++) {
                                String[] srcParts = parseFieldTransform(mappings.get(i)[0]);
                                String value = extractJsonFieldValue(obj, srcParts[0]);
                                value = applyTransform(value, srcParts[1]);
                                ps.setString(i + 2, value);
                            }
                        }

                        int rows = ps.executeUpdate();
                        if (rows > 0) {
                            upserted++;
                        } else {
                            // UPDATE matched 0 rows — no existing row for this ID
                            failed++;
                            if (lastError == null) {
                                String idValue = extractJsonFieldValue(obj, idFieldName);
                                idValue = applyTransform(idValue, idTransform);
                                lastError = "No matching row for " + config.idFieldTarget + "=" + idValue;
                            }
                        }
                    } catch (Exception e) {
                        failed++;
                        if (lastError == null) {
                            lastError = e.getMessage();
                            log.warn("First row error: {}", e.getMessage(), e);
                        }
                    }
                }
            }

            // 9. Update history and config
            String errorMsg = lastError != null ? "First error: " + lastError : null;
            updateHistoryRecord(conn, historyId, "success", fetched, upserted, failed, errorMsg);
            updateConfigLastRun(conn, configId, "success", upserted);

        } catch (Exception e) {
            // Update history with failure
            if (historyId > 0) {
                try (Connection conn2 = DBUtil.getConnection()) {
                    updateHistoryRecord(conn2, historyId, "failed", 0, 0, 0, e.getMessage());
                    updateConfigLastRun(conn2, configId, "failed", 0);
                } catch (SQLException ex) {
                    // Best effort
                }
            }
        }

        return historyId;
    }

    /**
     * Test a URL + auth configuration. Returns JSON field names from the first object.
     */
    public static TestResult testConnection(String url, String authType, String authConfig) {
        TestResult result = new TestResult();
        try {
            // Validate URL first
            if (url == null || url.trim().isEmpty()) {
                result.success = false;
                result.error = "URL is empty";
                return result;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                result.success = false;
                result.error = "URL must start with http:// or https://";
                return result;
            }

            // SSRF validation — block internal/private IPs
            validateUrlNotInternal(url);

            HttpRequest request = buildAuthenticatedRequest(url.trim(), authType, authConfig);
            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            result.statusCode = response.statusCode();

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                result.success = true;
                String body = response.body();

                if (body == null || body.trim().isEmpty()) {
                    result.success = false;
                    result.error = "Empty response body";
                    return result;
                }

                // Extract field names from the first JSON object
                result.fields = extractFieldNamesFromFirstObject(body);
                result.sampleCount = countJsonObjects(body);
            } else {
                result.success = false;
                result.error = "HTTP " + response.statusCode();
            }
        } catch (java.net.http.HttpConnectTimeoutException e) {
            result.success = false;
            result.error = "Connection timeout: " + e.getMessage();
        } catch (java.net.http.HttpTimeoutException e) {
            result.success = false;
            result.error = "Request timeout: " + e.getMessage();
        } catch (IllegalArgumentException e) {
            result.success = false;
            result.error = "Invalid URL: " + e.getMessage();
        } catch (Exception e) {
            result.success = false;
            result.error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        return result;
    }

    /**
     * Get column info for a whitelisted table.
     */
    public static List<ColumnInfo> getTableColumns(String tableName) throws SQLException {
        if (!ALLOWED_TABLES.contains(tableName.toLowerCase())) {
            throw new IllegalArgumentException("Table not allowed: " + tableName);
        }

        List<ColumnInfo> columns = new ArrayList<>();
        String sql = "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_KEY " +
                     "FROM INFORMATION_SCHEMA.COLUMNS " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? " +
                     "ORDER BY ORDINAL_POSITION";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ColumnInfo col = new ColumnInfo();
                    col.name = rs.getString("COLUMN_NAME");
                    col.type = rs.getString("DATA_TYPE");
                    col.nullable = "YES".equals(rs.getString("IS_NULLABLE"));
                    col.isKey = rs.getString("COLUMN_KEY") != null && !rs.getString("COLUMN_KEY").isEmpty();
                    columns.add(col);
                }
            }
        }
        return columns;
    }

    /**
     * Get the list of allowed table names.
     */
    public static Set<String> getAllowedTables() {
        return Collections.unmodifiableSet(ALLOWED_TABLES);
    }

    /**
     * Fetch a URL with authentication and return the raw response body.
     * Public so that SyncConfigServlet can use it for root path navigation.
     */
    public static String fetchUrl(String url, String authType, String authConfig) throws Exception {
        validateUrlNotInternal(url);
        HttpRequest request = buildAuthenticatedRequest(url, authType, authConfig);
        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    // --- Internal methods ---

    static HttpRequest buildAuthenticatedRequest(String url, String authType, String authConfigJson) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET();

        if (authConfigJson == null || authConfigJson.isEmpty()) {
            return builder.build();
        }

        switch (authType != null ? authType : "none") {
            case "api_key": {
                String headerName = extractJsonFieldValue(authConfigJson, "headerName");
                String keyValue = extractJsonFieldValue(authConfigJson, "keyValue");
                if (headerName != null && keyValue != null) {
                    builder.header(headerName, keyValue);
                }
                break;
            }
            case "bearer": {
                String token = extractJsonFieldValue(authConfigJson, "token");
                if (token != null) {
                    builder.header("Authorization", "Bearer " + token);
                }
                break;
            }
            case "basic": {
                String username = extractJsonFieldValue(authConfigJson, "username");
                String password = extractJsonFieldValue(authConfigJson, "password");
                if (username != null && password != null) {
                    String encoded = Base64.getEncoder().encodeToString(
                        (username + ":" + password).getBytes(StandardCharsets.UTF_8));
                    builder.header("Authorization", "Basic " + encoded);
                }
                break;
            }
            default:
                // no auth
                break;
        }

        return builder.build();
    }

    /**
     * Navigate a dot-path like "data.users" to find the array portion of JSON.
     */
    public static String navigateJsonPath(String jsonBody, String rootPath) {
        if (rootPath == null || rootPath.trim().isEmpty()) {
            return jsonBody.trim();
        }

        String current = jsonBody.trim();
        String[] parts = rootPath.split("\\.");

        for (String part : parts) {
            // Find "part": and extract its value
            Pattern p = Pattern.compile("\"" + Pattern.quote(part) + "\"\\s*:\\s*");
            Matcher m = p.matcher(current);
            if (!m.find()) {
                throw new RuntimeException("JSON path not found: " + part);
            }

            int valueStart = m.end();
            current = extractValueAt(current, valueStart);
        }

        return current;
    }

    /**
     * Extract a JSON value (object, array, string, number) starting at the given index.
     */
    private static String extractValueAt(String json, int start) {
        // Skip whitespace
        int i = start;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;

        if (i >= json.length()) return "";

        char first = json.charAt(i);
        if (first == '[' || first == '{') {
            // Find matching bracket
            char open = first;
            char close = first == '[' ? ']' : '}';
            int depth = 1;
            int j = i + 1;
            boolean inString = false;
            while (j < json.length() && depth > 0) {
                char c = json.charAt(j);
                if (inString) {
                    if (c == '\\') { j++; } // skip escaped char
                    else if (c == '"') { inString = false; }
                } else {
                    if (c == '"') { inString = true; }
                    else if (c == open) { depth++; }
                    else if (c == close) { depth--; }
                }
                j++;
            }
            return json.substring(i, j);
        } else if (first == '"') {
            // String value
            int j = i + 1;
            while (j < json.length()) {
                if (json.charAt(j) == '\\') { j += 2; continue; }
                if (json.charAt(j) == '"') break;
                j++;
            }
            return json.substring(i, j + 1);
        } else {
            // Number, boolean, null
            int j = i;
            while (j < json.length() && !Character.isWhitespace(json.charAt(j))
                   && json.charAt(j) != ',' && json.charAt(j) != '}' && json.charAt(j) != ']') {
                j++;
            }
            return json.substring(i, j);
        }
    }

    /**
     * Extract individual JSON objects from a JSON array string.
     */
    public static List<String> extractJsonObjects(String arrayJson) {
        List<String> objects = new ArrayList<>();
        String trimmed = arrayJson.trim();
        if (!trimmed.startsWith("[")) return objects;

        String inner = trimmed.substring(1, trimmed.length() - 1);
        int depth = 0;
        int start = -1;
        boolean inString = false;

        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (inString) {
                if (c == '\\') { i++; continue; }
                if (c == '"') { inString = false; }
            } else {
                if (c == '"') { inString = true; }
                else if (c == '{') {
                    if (depth == 0) start = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        objects.add(inner.substring(start, i + 1));
                        start = -1;
                    }
                }
            }
        }
        return objects;
    }

    /**
     * Extract a string value from a flat JSON object by key.
     * Handles both quoted strings and unquoted values (numbers, booleans, null).
     */
    public static String extractJsonFieldValue(String json, String key) {
        // Try string value first
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return unescapeJson(m.group(1));

        // Try number/boolean/null
        Pattern p2 = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([^,}\\]\\s]+)");
        Matcher m2 = p2.matcher(json);
        if (m2.find()) {
            String val = m2.group(1).trim();
            if ("null".equals(val)) return null;
            return val;
        }

        return null;
    }

    /**
     * Unescape JSON string escape sequences: \/ \\ \" \n \r \t
     */
    private static String unescapeJson(String s) {
        if (s == null || !s.contains("\\")) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '/': sb.append('/'); i++; break;
                    case '\\': sb.append('\\'); i++; break;
                    case '"': sb.append('"'); i++; break;
                    case 'n': sb.append('\n'); i++; break;
                    case 'r': sb.append('\r'); i++; break;
                    case 't': sb.append('\t'); i++; break;
                    default: sb.append(c); break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Parse a field name that may contain a |transform suffix.
     * E.g., "serverUrl|url_normalize" → ["serverUrl", "url_normalize"]
     * Returns String[2]: [fieldName, transformName or null]
     */
    public static String[] parseFieldTransform(String field) {
        if (field == null || !field.contains("|")) return new String[]{field, null};
        int pipe = field.indexOf('|');
        return new String[]{field.substring(0, pipe), field.substring(pipe + 1)};
    }

    /**
     * Apply a named value transform.
     * Supported: "url_normalize" — strips http(s):// prefix and /path suffix, lowercases.
     */
    public static String applyTransform(String value, String transform) {
        if (value == null || transform == null) return value;
        if ("url_normalize".equals(transform)) {
            String result = value.replaceFirst("^https?://", "");
            int slash = result.indexOf('/');
            if (slash >= 0) result = result.substring(0, slash);
            return result.trim().toLowerCase();
        }
        return value;
    }

    /**
     * Extract all top-level field names from the first JSON object found in the response.
     */
    public static List<String> extractFieldNamesFromFirstObject(String json) {
        List<String> fields = new ArrayList<>();
        String trimmed = json.trim();

        // Find first { in the body
        String firstObj = null;
        if (trimmed.startsWith("{")) {
            firstObj = trimmed;
        } else if (trimmed.startsWith("[")) {
            List<String> objects = extractJsonObjects(trimmed);
            if (!objects.isEmpty()) firstObj = objects.get(0);
        } else {
            // Try to find an array within the JSON
            int arrStart = trimmed.indexOf('[');
            if (arrStart >= 0) {
                String arrPart = extractValueAt(trimmed, arrStart);
                List<String> objects = extractJsonObjects(arrPart);
                if (!objects.isEmpty()) firstObj = objects.get(0);
            }
        }

        if (firstObj == null) return fields;

        // Extract keys from the first-level of the object
        // Pattern: "key" :
        Pattern p = Pattern.compile("\"([^\"]+)\"\\s*:");
        Matcher m = p.matcher(firstObj);
        // Only get top-level keys (depth 0)
        int depth = 0;
        boolean inString = false;
        int pos = 0;

        for (int i = 0; i < firstObj.length(); i++) {
            char c = firstObj.charAt(i);
            if (inString) {
                if (c == '\\') { i++; continue; }
                if (c == '"') { inString = false; }
            } else {
                if (c == '"') { inString = true; }
                else if (c == '{' || c == '[') { depth++; }
                else if (c == '}' || c == ']') { depth--; }
                else if (c == ':' && depth == 1) {
                    // This colon is at the top level of the object
                    // Find the key before it
                    String before = firstObj.substring(pos, i);
                    Pattern kp = Pattern.compile("\"([^\"]+)\"\\s*$");
                    Matcher km = kp.matcher(before);
                    if (km.find()) {
                        String fieldName = km.group(1);
                        if (!fields.contains(fieldName)) {
                            fields.add(fieldName);
                        }
                    }
                }
            }
            if (c == ',' && depth == 1) {
                pos = i + 1;
            }
        }

        return fields;
    }

    /**
     * Count the number of JSON objects in a response (at any depth).
     */
    private static int countJsonObjects(String json) {
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) {
            return extractJsonObjects(trimmed).size();
        }
        // Try to find array within the JSON
        int arrStart = trimmed.indexOf('[');
        if (arrStart >= 0) {
            String arr = extractValueAt(trimmed, arrStart);
            return extractJsonObjects(arr).size();
        }
        return trimmed.startsWith("{") ? 1 : 0;
    }

    /**
     * Parse field_mappings JSON: [{"source":"x","target":"y","lookup":"table.column"}, ...]
     * Returns String[3]: [source, target, lookup] where lookup may be null.
     * Lookup format: "table.column" — resolves the JSON value via
     *   (SELECT id FROM table WHERE column = ?) before inserting into target column.
     */
    public static List<String[]> parseMappings(String mappingsJson) {
        List<String[]> result = new ArrayList<>();
        if (mappingsJson == null || mappingsJson.trim().isEmpty()) return result;

        List<String> objects = extractJsonObjects(mappingsJson);
        for (String obj : objects) {
            String source = extractJsonFieldValue(obj, "source");
            String target = extractJsonFieldValue(obj, "target");
            String lookup = extractJsonFieldValue(obj, "lookup");
            if (source != null && target != null) {
                result.add(new String[]{source, target, lookup});
            }
        }
        return result;
    }

    /**
     * Build INSERT ... ON DUPLICATE KEY UPDATE SQL.
     * Supports FK lookup: if a mapping has lookup="table.column", the placeholder
     * becomes (SELECT id FROM table WHERE column = ?) instead of plain ?.
     * Column names are validated before this method is called.
     */
    public static String buildUpsertSql(String table, String idColumn, List<String[]> mappings) {
        // Validate identifier names (only alphanumeric + underscore)
        validateIdentifier(table);
        validateIdentifier(idColumn);

        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        StringBuilder updates = new StringBuilder();

        // ID column
        columns.append('`').append(idColumn).append('`');
        placeholders.append("?");

        // Mapped columns
        for (String[] m : mappings) {
            validateIdentifier(m[1]); // target column
            columns.append(", `").append(m[1]).append('`');

            // Check for FK lookup
            String lookup = m.length > 2 ? m[2] : null;
            if (lookup != null && !lookup.isEmpty() && lookup.contains(".")) {
                String[] parts = lookup.split("\\.", 2);
                validateIdentifier(parts[0]); // lookup table
                validateIdentifier(parts[1]); // lookup column
                // Validate lookup table is also allowed
                if (!ALLOWED_TABLES.contains(parts[0].toLowerCase())) {
                    throw new IllegalArgumentException("Lookup table not allowed: " + parts[0]);
                }
                String subquery = "(SELECT `id` FROM `" + parts[0] + "` WHERE `" + parts[1] + "` = ? LIMIT 1)";
                placeholders.append(", ").append(subquery);
            } else {
                placeholders.append(", ?");
            }

            updates.append(updates.length() > 0 ? ", " : "");
            updates.append('`').append(m[1]).append("` = VALUES(`").append(m[1]).append("`)");
        }

        return "INSERT INTO `" + table + "` (" + columns + ") VALUES (" + placeholders + ") " +
               "ON DUPLICATE KEY UPDATE " + updates;
    }

    /**
     * Build UPDATE ... SET ... WHERE SQL for update-only mode.
     * Only updates existing rows, never inserts new ones.
     * Supports FK lookup in SET clause.
     * Parameter order: SET values first, then WHERE id value last.
     */
    public static String buildUpdateSql(String table, String idColumn, List<String[]> mappings) {
        validateIdentifier(table);
        validateIdentifier(idColumn);

        StringBuilder sets = new StringBuilder();

        for (String[] m : mappings) {
            validateIdentifier(m[1]);
            if (sets.length() > 0) sets.append(", ");

            String lookup = m.length > 2 ? m[2] : null;
            if (lookup != null && !lookup.isEmpty() && lookup.contains(".")) {
                String[] parts = lookup.split("\\.", 2);
                validateIdentifier(parts[0]);
                validateIdentifier(parts[1]);
                if (!ALLOWED_TABLES.contains(parts[0].toLowerCase())) {
                    throw new IllegalArgumentException("Lookup table not allowed: " + parts[0]);
                }
                sets.append("`").append(m[1]).append("` = (SELECT `id` FROM `")
                   .append(parts[0]).append("` WHERE `").append(parts[1]).append("` = ? LIMIT 1)");
            } else {
                sets.append("`").append(m[1]).append("` = ?");
            }
        }

        return "UPDATE `" + table + "` SET " + sets + " WHERE `" + idColumn + "` = ?";
    }

    /**
     * Validate an SQL identifier contains only safe characters.
     */
    public static void validateIdentifier(String name) {
        if (name == null || !name.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + name);
        }
    }

    /**
     * Validate that all target columns exist in the table.
     */
    public static void validateColumns(Connection conn, String table, List<String> columns)
            throws SQLException {
        Set<String> validColumns = new HashSet<>();
        String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    validColumns.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }
        }

        for (String col : columns) {
            if (!validColumns.contains(col.toLowerCase())) {
                throw new RuntimeException("Column not found: " + col + " in table " + table);
            }
        }
    }

    // --- DB operations ---

    private static SyncConfig loadConfig(Connection conn, int configId) throws SQLException {
        String sql = "SELECT * FROM sync_configs WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, configId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                SyncConfig config = new SyncConfig();
                config.id = rs.getInt("id");
                config.name = rs.getString("name");
                config.sourceUrl = rs.getString("source_url");
                config.authType = rs.getString("auth_type");
                config.authConfig = CryptoUtil.decrypt(rs.getString("auth_config"));
                config.jsonRootPath = rs.getString("json_root_path");
                config.targetTable = rs.getString("target_table");
                config.idFieldSource = rs.getString("id_field_source");
                config.idFieldTarget = rs.getString("id_field_target");
                config.fieldMappings = rs.getString("field_mappings");
                config.scheduleMinutes = rs.getInt("schedule_minutes");
                config.isActive = rs.getBoolean("is_active");
                try { config.updateOnly = rs.getBoolean("update_only"); } catch (SQLException ignored) {}
                return config;
            }
        }
    }

    private static long createHistoryRecord(Connection conn, int configId, Integer triggeredBy)
            throws SQLException {
        String sql = "INSERT INTO sync_run_history (config_id, triggered_by) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, configId);
            if (triggeredBy != null) {
                ps.setInt(2, triggeredBy);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0;
            }
        }
    }

    private static void updateHistoryRecord(Connection conn, long historyId, String status,
            int fetched, int upserted, int failed, String errorMessage) throws SQLException {
        String sql = "UPDATE sync_run_history SET status = ?, completed_at = NOW(), " +
                     "records_fetched = ?, records_upserted = ?, records_failed = ?, " +
                     "error_message = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, fetched);
            ps.setInt(3, upserted);
            ps.setInt(4, failed);
            ps.setString(5, errorMessage);
            ps.setLong(6, historyId);
            ps.executeUpdate();
        }
    }

    private static void updateConfigLastRun(Connection conn, int configId, String status, int count)
            throws SQLException {
        String sql = "UPDATE sync_configs SET last_run_at = NOW(), last_run_status = ?, " +
                     "last_run_count = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, count);
            ps.setInt(3, configId);
            ps.executeUpdate();
        }
    }

    // --- Data classes ---

    public static class SyncConfig {
        public int id;
        public String name, sourceUrl, authType, authConfig;
        public String jsonRootPath, targetTable, idFieldSource, idFieldTarget;
        public String fieldMappings;
        public int scheduleMinutes;
        public boolean isActive;
        public boolean updateOnly;
    }

    public static class TestResult {
        public boolean success;
        public int statusCode;
        public String error;
        public List<String> fields = new ArrayList<>();
        public int sampleCount;
        public String rawPreview;
    }

    public static class ColumnInfo {
        public String name, type;
        public boolean nullable, isKey;
    }
}
