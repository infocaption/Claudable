package com.infocaption.dashboard.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager for upstream MCP connections.
 * Thread-safe. Manages connection lifecycle, tool aggregation, and routing.
 */
public class McpClientManager {

    private static final Logger log = LoggerFactory.getLogger(McpClientManager.class);
    private static final McpClientManager INSTANCE = new McpClientManager();

    /** Active connections keyed by server ID */
    private final ConcurrentHashMap<Integer, McpConnection> connections = new ConcurrentHashMap<>();

    /** Cached server configs keyed by server ID */
    private final ConcurrentHashMap<Integer, ServerConfig> serverConfigs = new ConcurrentHashMap<>();

    private McpClientManager() {}

    public static McpClientManager getInstance() {
        return INSTANCE;
    }

    /**
     * Load all active server configs from DB and connect.
     */
    public void initialize() {
        log.info("Initializing MCP Client Manager...");
        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement()) {

            // Ensure tables exist
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS mcp_servers (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  name VARCHAR(200) NOT NULL," +
                "  transport_type ENUM('http','stdio') NOT NULL DEFAULT 'http'," +
                "  endpoint_url VARCHAR(1000) NULL," +
                "  command VARCHAR(500) NULL," +
                "  command_args TEXT NULL," +
                "  auth_type ENUM('none','bearer','api_key','basic') NOT NULL DEFAULT 'none'," +
                "  auth_config TEXT NULL," +
                "  tool_prefix VARCHAR(50) NOT NULL UNIQUE," +
                "  cached_tools TEXT NULL," +
                "  is_active TINYINT(1) NOT NULL DEFAULT 1," +
                "  last_connected TIMESTAMP NULL," +
                "  last_error TEXT NULL," +
                "  created_by INT NULL," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS mcp_audit_log (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  user_id INT NULL," +
                "  server_id INT NULL," +
                "  method VARCHAR(50) NOT NULL," +
                "  tool_name VARCHAR(200) NULL," +
                "  request_summary TEXT NULL," +
                "  response_status ENUM('success','error','timeout') NOT NULL," +
                "  error_message TEXT NULL," +
                "  duration_ms INT NULL," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL," +
                "  FOREIGN KEY (server_id) REFERENCES mcp_servers(id) ON DELETE SET NULL," +
                "  INDEX idx_audit_created (created_at)," +
                "  INDEX idx_audit_user (user_id)," +
                "  INDEX idx_audit_server (server_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            log.info("MCP tables verified/created");

            // Load active servers
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT * FROM mcp_servers WHERE is_active = 1")) {
                while (rs.next()) {
                    ServerConfig config = mapServerConfig(rs);
                    serverConfigs.put(config.id, config);
                    // Don't auto-connect on startup — connect lazily on first use
                }
            }

            log.info("MCP Client Manager initialized with {} active server configs",
                    serverConfigs.size());

            // Purge old audit logs
            purgeOldAuditLogs();

        } catch (SQLException e) {
            log.warn("Could not initialize MCP Client Manager: {}", e.getMessage());
        }
    }

    /**
     * Get or create a connection for a server.
     */
    public McpConnection getConnection(int serverId) throws IOException {
        McpConnection conn = connections.get(serverId);
        if (conn != null && conn.isAlive()) return conn;

        // Need to connect
        ServerConfig config = serverConfigs.get(serverId);
        if (config == null) {
            config = loadServerConfig(serverId);
            if (config == null) throw new IOException("Server not found: " + serverId);
            serverConfigs.put(serverId, config);
        }

        conn = createConnection(config);
        connections.put(serverId, conn);
        updateLastConnected(serverId, null);
        return conn;
    }

    /**
     * Get aggregated tools from all active servers.
     * Each tool name is prefixed with {prefix}__
     * Returns a JSON array string of tool objects.
     */
    public String getAggregatedTools() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        for (Map.Entry<Integer, ServerConfig> entry : serverConfigs.entrySet()) {
            ServerConfig config = entry.getValue();
            if (!config.active) continue;

            String cachedTools = config.cachedTools;
            if (cachedTools == null || cachedTools.isEmpty()) continue;

            // Parse the cached tools array and prefix each tool name
            // cachedTools is the raw tools/list result, expected format:
            // {"result":{"tools":[...]}} or just [...]
            String toolsArray = extractToolsArray(cachedTools);
            if (toolsArray == null) continue;

            // Split tools and re-prefix each
            List<String> tools = splitJsonArray(toolsArray);
            for (String tool : tools) {
                // Extract the original name
                String origName = JsonUtil.extractJsonString(tool, "name");
                if (origName == null) continue;

                String prefixedName = config.toolPrefix + "__" + origName;

                if (!first) sb.append(",");
                first = false;

                // Replace the name in the tool JSON
                sb.append(replaceToolName(tool, origName, prefixedName));
            }
        }

        // Append Knowledge Base collection tools
        String kbTools = KnowledgeBaseUtil.getCollectionTools();
        if (kbTools != null && kbTools.length() > 2) { // more than "[]"
            String kbInner = kbTools.substring(1, kbTools.length() - 1); // strip [ ]
            if (!kbInner.isEmpty()) {
                if (!first) sb.append(",");
                sb.append(kbInner);
            }
        }

        sb.append("]");
        return sb.toString();
    }

    /**
     * Route a tool call to the correct upstream server or KB collection.
     * @param prefixedToolName e.g., "github__list_repos" or "support__search_documents"
     * @param arguments raw JSON arguments string
     * @return raw JSON-RPC response from upstream, or KB result wrapped in JSON-RPC format
     */
    public ToolCallResult routeToolCall(String prefixedToolName, String arguments) throws IOException {
        int sep = prefixedToolName.indexOf("__");
        if (sep < 0) throw new IOException("Invalid tool name format (missing prefix): " + prefixedToolName);

        String prefix = prefixedToolName.substring(0, sep);
        String toolName = prefixedToolName.substring(sep + 2);

        // Check Knowledge Base collections first (internal routing, no HTTP)
        if (KnowledgeBaseUtil.isKbPrefix(prefix)) {
            String result = KnowledgeBaseUtil.handleToolCall(prefix, toolName, arguments);
            // Wrap in JSON-RPC response format for consistency
            String wrappedResponse = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":" + result + "}";
            return new ToolCallResult(-1, wrappedResponse); // -1 = internal KB
        }

        // Find upstream server by prefix
        ServerConfig targetConfig = null;
        for (ServerConfig config : serverConfigs.values()) {
            if (config.toolPrefix.equals(prefix) && config.active) {
                targetConfig = config;
                break;
            }
        }
        if (targetConfig == null) {
            throw new IOException("No active MCP server with prefix: " + prefix);
        }

        McpConnection conn = getConnection(targetConfig.id);

        // Build JSON-RPC request
        String request = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":" +
                "{\"name\":" + JsonUtil.quote(toolName) +
                ",\"arguments\":" + (arguments != null ? arguments : "{}") + "}}";

        String response = conn.sendRequest(request);
        return new ToolCallResult(targetConfig.id, response);
    }

    /**
     * Refresh cached tools for a specific server.
     * Returns the tools/list response.
     */
    public String refreshServer(int serverId) throws IOException {
        // Close existing connection so we get a fresh one with new handshake
        McpConnection old = connections.remove(serverId);
        if (old != null) old.close();

        McpConnection conn = getConnection(serverId);

        // listTools() handles initialize handshake automatically for HTTP connections
        String response = conn.listTools();

        // Cache the response
        updateCachedTools(serverId, response);

        // Update in-memory config
        ServerConfig config = serverConfigs.get(serverId);
        if (config != null) {
            config.cachedTools = response;
        }

        updateLastConnected(serverId, null);
        return response;
    }

    /**
     * Remove a server and close its connection.
     */
    public void removeServer(int serverId) {
        McpConnection conn = connections.remove(serverId);
        if (conn != null) conn.close();
        serverConfigs.remove(serverId);
    }

    /**
     * Reload a server config from DB.
     */
    public void reloadServerConfig(int serverId) {
        ServerConfig config = loadServerConfig(serverId);
        if (config != null) {
            serverConfigs.put(serverId, config);
            // Close existing connection so it reconnects with new config
            McpConnection conn = connections.remove(serverId);
            if (conn != null) conn.close();
        }
    }

    /**
     * Get all server configs (for admin display).
     */
    public List<ServerConfig> getAllServerConfigs() {
        List<ServerConfig> configs = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM mcp_servers ORDER BY name")) {
            while (rs.next()) {
                configs.add(mapServerConfig(rs));
            }
        } catch (SQLException e) {
            log.error("Error loading MCP server configs: {}", e.getMessage());
        }
        return configs;
    }

    /**
     * Log an audit entry for an MCP operation.
     */
    public void logAudit(Integer userId, Integer serverId, String method, String toolName,
                         String requestSummary, String responseStatus, String errorMessage, Integer durationMs) {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO mcp_audit_log (user_id, server_id, method, tool_name, request_summary, " +
                "response_status, error_message, duration_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            if (userId != null) ps.setInt(1, userId); else ps.setNull(1, Types.INTEGER);
            if (serverId != null) ps.setInt(2, serverId); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, method);
            ps.setString(4, toolName);
            ps.setString(5, requestSummary != null && requestSummary.length() > 500
                    ? requestSummary.substring(0, 500) : requestSummary);
            ps.setString(6, responseStatus);
            ps.setString(7, errorMessage);
            if (durationMs != null) ps.setInt(8, durationMs); else ps.setNull(8, Types.INTEGER);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("Failed to write MCP audit log: {}", e.getMessage());
        }
    }

    /**
     * Get recent audit log entries.
     */
    public String getAuditLog(int limit) {
        StringBuilder sb = new StringBuilder("[");
        String sql = "SELECT a.*, s.name AS server_name, s.tool_prefix, u.email AS user_email " +
                "FROM mcp_audit_log a " +
                "LEFT JOIN mcp_servers s ON a.server_id = s.id " +
                "LEFT JOIN users u ON a.user_id = u.id " +
                "ORDER BY a.created_at DESC LIMIT ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            boolean first = true;
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("{");
                    sb.append("\"id\":").append(rs.getLong("id"));
                    sb.append(",\"userId\":").append(rs.getObject("user_id") != null ? rs.getInt("user_id") : "null");
                    sb.append(",\"userEmail\":").append(JsonUtil.quote(rs.getString("user_email")));
                    sb.append(",\"serverId\":").append(rs.getObject("server_id") != null ? rs.getInt("server_id") : "null");
                    sb.append(",\"serverName\":").append(JsonUtil.quote(rs.getString("server_name")));
                    sb.append(",\"toolPrefix\":").append(JsonUtil.quote(rs.getString("tool_prefix")));
                    sb.append(",\"method\":").append(JsonUtil.quote(rs.getString("method")));
                    sb.append(",\"toolName\":").append(JsonUtil.quote(rs.getString("tool_name")));
                    sb.append(",\"requestSummary\":").append(JsonUtil.quote(rs.getString("request_summary")));
                    sb.append(",\"responseStatus\":").append(JsonUtil.quote(rs.getString("response_status")));
                    sb.append(",\"errorMessage\":").append(JsonUtil.quote(rs.getString("error_message")));
                    sb.append(",\"durationMs\":").append(rs.getObject("duration_ms") != null ? rs.getInt("duration_ms") : "null");
                    sb.append(",\"createdAt\":").append(JsonUtil.quote(rs.getString("created_at")));
                    sb.append("}");
                }
            }
        } catch (SQLException e) {
            log.error("Error loading MCP audit log: {}", e.getMessage());
        }
        sb.append("]");
        return sb.toString();
    }

    // ==================== Private helpers ====================

    private McpConnection createConnection(ServerConfig config) throws IOException {
        if ("stdio".equals(config.transportType)) {
            List<String> args = parseCommandArgs(config.commandArgs);
            StdioMcpConnection conn = new StdioMcpConnection(config.command, args,
                    AppConfig.getInt("mcp.stdioTimeout", 30));
            conn.start();
            return conn;
        } else {
            HttpMcpConnection conn = new HttpMcpConnection(config.endpointUrl, config.authType, config.authConfig,
                    AppConfig.getInt("mcp.httpTimeout", 30));
            conn.setServerId(config.id);
            return conn;
        }
    }

    private List<String> parseCommandArgs(String commandArgs) {
        if (commandArgs == null || commandArgs.trim().isEmpty()) return Collections.emptyList();
        // Parse JSON array of strings
        List<String> result = JsonUtil.extractJsonStringList(commandArgs, null);
        if (result != null) return result;

        // Fallback: try to parse as bare JSON array
        List<String> args = new ArrayList<>();
        String trimmed = commandArgs.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            // Wrap in object for extractJsonStringList
            result = JsonUtil.extractJsonStringList("{\"a\":" + trimmed + "}", "a");
            if (result != null) return result;
        }
        return args;
    }

    private ServerConfig loadServerConfig(int serverId) {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM mcp_servers WHERE id = ?")) {
            ps.setInt(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapServerConfig(rs);
            }
        } catch (SQLException e) {
            log.error("Error loading MCP server config {}: {}", serverId, e.getMessage());
        }
        return null;
    }

    private static ServerConfig mapServerConfig(ResultSet rs) throws SQLException {
        ServerConfig c = new ServerConfig();
        c.id = rs.getInt("id");
        c.name = rs.getString("name");
        c.transportType = rs.getString("transport_type");
        c.endpointUrl = rs.getString("endpoint_url");
        c.command = rs.getString("command");
        c.commandArgs = rs.getString("command_args");
        c.authType = rs.getString("auth_type");
        c.authConfig = CryptoUtil.decrypt(rs.getString("auth_config"));
        c.toolPrefix = rs.getString("tool_prefix");
        c.cachedTools = rs.getString("cached_tools");
        c.active = rs.getBoolean("is_active");
        c.lastConnected = rs.getString("last_connected");
        c.lastError = rs.getString("last_error");
        c.createdBy = rs.getObject("created_by") != null ? rs.getInt("created_by") : null;
        c.createdAt = rs.getString("created_at");
        c.updatedAt = rs.getString("updated_at");
        return c;
    }

    private void updateLastConnected(int serverId, String error) {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE mcp_servers SET last_connected = NOW(), last_error = ? WHERE id = ?")) {
            ps.setString(1, error);
            ps.setInt(2, serverId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.debug("Failed to update last_connected: {}", e.getMessage());
        }
    }

    private void updateCachedTools(int serverId, String cachedTools) {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE mcp_servers SET cached_tools = ?, last_connected = NOW(), last_error = NULL WHERE id = ?")) {
            ps.setString(1, cachedTools);
            ps.setInt(2, serverId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("Failed to update cached tools: {}", e.getMessage());
        }
    }

    private void purgeOldAuditLogs() {
        int retentionDays = AppConfig.getInt("mcp.auditRetentionDays", 30);
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM mcp_audit_log WHERE created_at < DATE_SUB(NOW(), INTERVAL ? DAY)")) {
            ps.setInt(1, retentionDays);
            int deleted = ps.executeUpdate();
            if (deleted > 0) log.info("Purged {} old MCP audit log entries", deleted);
        } catch (SQLException e) {
            log.debug("Failed to purge audit logs: {}", e.getMessage());
        }
    }

    /**
     * Extract the tools array from a JSON-RPC response or cached string.
     * Handles both {"result":{"tools":[...]}} and {"tools":[...]} and bare [...].
     */
    private String extractToolsArray(String json) {
        if (json == null) return null;
        String trimmed = json.trim();

        // Try {"result":{"tools":[...]}}
        String result = JsonUtil.extractJsonObject(trimmed, "result");
        if (result != null) {
            String arr = JsonUtil.extractJsonArray(result, "tools");
            if (arr != null) return arr;
        }

        // Try {"tools":[...]}
        String arr = JsonUtil.extractJsonArray(trimmed, "tools");
        if (arr != null) return arr;

        // Bare array
        if (trimmed.startsWith("[")) return trimmed;

        return null;
    }

    /**
     * Split a JSON array into individual object strings.
     */
    private List<String> splitJsonArray(String arrayJson) {
        List<String> items = new ArrayList<>();
        if (arrayJson == null || arrayJson.length() < 2) return items;

        // Remove outer brackets
        String inner = arrayJson.substring(1, arrayJson.length() - 1).trim();
        if (inner.isEmpty()) return items;

        int depth = 0;
        int start = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') depth--;
            else if (c == ',' && depth == 0) {
                String item = inner.substring(start, i).trim();
                if (!item.isEmpty()) items.add(item);
                start = i + 1;
            }
        }
        // Last item
        String last = inner.substring(start).trim();
        if (!last.isEmpty()) items.add(last);

        return items;
    }

    /**
     * Replace the "name" field value in a tool JSON object.
     */
    private String replaceToolName(String toolJson, String oldName, String newName) {
        // Replace "name":"oldName" with "name":"newName"
        return toolJson.replace("\"name\":\"" + oldName + "\"", "\"name\":\"" + newName + "\"");
    }

    /**
     * Shutdown all connections.
     */
    public void shutdown() {
        log.info("Shutting down MCP Client Manager...");
        for (McpConnection conn : connections.values()) {
            try { conn.close(); } catch (Exception e) { /* ignore */ }
        }
        connections.clear();
        serverConfigs.clear();
    }

    // ==================== Inner classes ====================

    public static class ServerConfig {
        public int id;
        public String name;
        public String transportType;
        public String endpointUrl;
        public String command;
        public String commandArgs;
        public String authType;
        public String authConfig;
        public String toolPrefix;
        public String cachedTools;
        public boolean active;
        public String lastConnected;
        public String lastError;
        public Integer createdBy;
        public String createdAt;
        public String updatedAt;

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"id\":").append(id);
            sb.append(",\"name\":").append(JsonUtil.quote(name));
            sb.append(",\"transportType\":").append(JsonUtil.quote(transportType));
            sb.append(",\"endpointUrl\":").append(JsonUtil.quote(endpointUrl));
            sb.append(",\"command\":").append(JsonUtil.quote(command));
            sb.append(",\"commandArgs\":").append(JsonUtil.quote(commandArgs));
            sb.append(",\"authType\":").append(JsonUtil.quote(authType));
            sb.append(",\"toolPrefix\":").append(JsonUtil.quote(toolPrefix));
            sb.append(",\"isActive\":").append(active);
            sb.append(",\"lastConnected\":").append(JsonUtil.quote(lastConnected));
            sb.append(",\"lastError\":").append(JsonUtil.quote(lastError));
            sb.append(",\"createdBy\":").append(createdBy != null ? createdBy : "null");
            sb.append(",\"createdAt\":").append(JsonUtil.quote(createdAt));
            sb.append(",\"updatedAt\":").append(JsonUtil.quote(updatedAt));

            // Count cached tools
            int toolCount = 0;
            if (cachedTools != null && !cachedTools.isEmpty()) {
                String arr = null;
                String result = JsonUtil.extractJsonObject(cachedTools, "result");
                if (result != null) arr = JsonUtil.extractJsonArray(result, "tools");
                if (arr == null) arr = JsonUtil.extractJsonArray(cachedTools, "tools");
                if (arr != null) {
                    // Count commas at depth 1
                    int d = 0;
                    for (int i = 1; i < arr.length() - 1; i++) {
                        char c = arr.charAt(i);
                        if (c == '{') d++;
                        else if (c == '}') d--;
                        else if (c == ',' && d == 0) toolCount++;
                    }
                    if (arr.length() > 2) toolCount++; // n commas = n+1 items
                }
            }
            sb.append(",\"toolCount\":").append(toolCount);

            sb.append("}");
            return sb.toString();
        }
    }

    public static class ToolCallResult {
        public final int serverId;
        public final String response;

        public ToolCallResult(int serverId, String response) {
            this.serverId = serverId;
            this.response = response;
        }
    }
}
