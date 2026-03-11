package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.AdminUtil;
import com.infocaption.dashboard.util.AppConfig;
import com.infocaption.dashboard.util.CryptoUtil;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.HttpMcpConnection;
import com.infocaption.dashboard.util.JsonUtil;
import com.infocaption.dashboard.util.McpClientManager;
import com.infocaption.dashboard.util.SyncExecutor;
import com.infocaption.dashboard.util.StdioMcpConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.*;

/**
 * MCP Admin Servlet — CRUD operations for MCP server configurations.
 *
 * GET    /api/mcp/admin/servers              — List all MCP servers
 * POST   /api/mcp/admin/servers              — Create or update (if body has "id") MCP server config
 * DELETE  /api/mcp/admin/servers?id=N         — Delete a server
 * POST   /api/mcp/admin/servers/test?id=N     — Test connectivity (refresh tools)
 * GET    /api/mcp/admin/servers/tools?id=N    — List tools from a specific server
 * POST   /api/mcp/admin/servers/refresh?id=N  — Refresh cached tools
 * GET    /api/mcp/admin/audit                 — Recent audit log (limit param, default 50)
 */
public class McpAdminServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(McpAdminServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String pathInfo = req.getPathInfo();

        // OAuth callback does NOT require admin — it's a redirect from external OAuth server
        // but we verify session state to ensure the admin initiated the flow
        if (pathInfo != null && pathInfo.equals("/oauth/callback")) {
            handleOAuthCallback(req, resp);
            return;
        }

        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        if (pathInfo != null && pathInfo.equals("/servers")) {
            handleListServers(req, resp);
        } else if (pathInfo != null && pathInfo.equals("/servers/tools")) {
            handleGetTools(req, resp);
        } else if (pathInfo != null && pathInfo.equals("/audit")) {
            handleGetAuditLog(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.equals("/servers")) {
            handleCreateOrUpdate(req, resp, admin);
        } else if (pathInfo != null && pathInfo.equals("/servers/test")) {
            handleTestServer(req, resp);
        } else if (pathInfo != null && pathInfo.equals("/servers/refresh")) {
            handleRefreshServer(req, resp);
        } else if (pathInfo != null && pathInfo.equals("/servers/oauth/authorize")) {
            handleOAuthAuthorize(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.equals("/servers")) {
            handleDeleteServer(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    // ==================== List Servers ====================

    /**
     * GET /api/mcp/admin/servers — list all MCP server configurations.
     */
    private void handleListServers(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        for (McpClientManager.ServerConfig config : McpClientManager.getInstance().getAllServerConfigs()) {
            if (!first) json.append(",");
            first = false;
            json.append(config.toJson());
        }

        json.append("]");
        resp.getWriter().write(json.toString());
    }

    // ==================== Create / Update ====================

    /**
     * POST /api/mcp/admin/servers — create or update an MCP server config.
     * Body: {"id": N (optional, triggers update), "name": "...", "transportType": "http"|"stdio",
     *        "endpointUrl": "...", "command": "...", "commandArgs": "...",
     *        "authType": "none"|"bearer"|"api_key"|"basic", "authConfig": "{...}",
     *        "toolPrefix": "...", "isActive": true|false}
     */
    private void handleCreateOrUpdate(HttpServletRequest req, HttpServletResponse resp, User admin)
            throws IOException {

        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        String bodyStr = body.toString();

        int id = JsonUtil.extractJsonInt(bodyStr, "id");
        String name = JsonUtil.extractJsonString(bodyStr, "name");
        String transportType = JsonUtil.extractJsonString(bodyStr, "transportType");
        String endpointUrl = JsonUtil.extractJsonString(bodyStr, "endpointUrl");
        String command = JsonUtil.extractJsonString(bodyStr, "command");
        String commandArgs = JsonUtil.extractJsonString(bodyStr, "commandArgs");
        String authType = JsonUtil.extractJsonString(bodyStr, "authType");
        // authConfig can be sent as a JSON object or as a JSON string — try both
        String authConfig = JsonUtil.extractJsonObject(bodyStr, "authConfig");
        if (authConfig == null) {
            // JS sends authConfig as a string value like "{\"token\":\"...\"}"
            authConfig = JsonUtil.extractJsonString(bodyStr, "authConfig");
        }
        String toolPrefix = JsonUtil.extractJsonString(bodyStr, "toolPrefix");
        boolean isActive = JsonUtil.extractJsonBoolean(bodyStr, "isActive");

        // Validate name
        if (name == null || name.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Name is required\"}");
            return;
        }

        // Validate transportType
        if (transportType == null || (!"http".equals(transportType) && !"stdio".equals(transportType))) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"transportType must be 'http' or 'stdio'\"}");
            return;
        }

        // Auto-generate toolPrefix from name if not provided
        if (toolPrefix == null || toolPrefix.trim().isEmpty()) {
            toolPrefix = name.trim().toLowerCase().replaceAll("[^a-z0-9]", "_");
            if (toolPrefix.length() > 50) {
                toolPrefix = toolPrefix.substring(0, 50);
            }
        }

        // Validate toolPrefix: alphanumeric + underscore only, max 50 chars
        if (!toolPrefix.matches("^[a-zA-Z0-9_]+$")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"toolPrefix must contain only alphanumeric characters and underscores\"}");
            return;
        }
        if (toolPrefix.length() > 50) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"toolPrefix must be 50 characters or fewer\"}");
            return;
        }

        // Transport-specific validation
        if ("http".equals(transportType)) {
            if (endpointUrl == null || endpointUrl.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"endpointUrl is required for HTTP transport\"}");
                return;
            }
            // SSRF validation
            try {
                SyncExecutor.validateUrlNotInternal(endpointUrl.trim());
            } catch (IllegalArgumentException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":" + JsonUtil.quote("Invalid URL: " + e.getMessage()) + "}");
                return;
            }
        } else {
            // stdio
            if (command == null || command.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"command is required for stdio transport\"}");
                return;
            }
            // Command whitelist validation
            try {
                StdioMcpConnection.validateCommand(command.trim());
            } catch (IOException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":" + JsonUtil.quote("Invalid command: " + e.getMessage()) + "}");
                return;
            }
        }

        // Default authType
        if (authType == null || authType.trim().isEmpty()) {
            authType = "none";
        }

        try (Connection conn = DBUtil.getConnection()) {
            if (id > 0) {
                // Update existing server
                String sql = "UPDATE mcp_servers SET name=?, transport_type=?, endpoint_url=?, " +
                             "command=?, command_args=?, auth_type=?, auth_config=?, tool_prefix=?, " +
                             "is_active=? WHERE id=?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, name.trim());
                    ps.setString(2, transportType);
                    ps.setString(3, endpointUrl != null ? endpointUrl.trim() : null);
                    ps.setString(4, command != null ? command.trim() : null);
                    ps.setString(5, commandArgs);
                    ps.setString(6, authType);
                    ps.setString(7, CryptoUtil.encrypt(authConfig));
                    ps.setString(8, toolPrefix.trim());
                    ps.setBoolean(9, isActive);
                    ps.setInt(10, id);
                    int rows = ps.executeUpdate();

                    if (rows == 0) {
                        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        resp.getWriter().write("{\"error\":\"Server not found\"}");
                        return;
                    }

                    McpClientManager.getInstance().reloadServerConfig(id);
                    resp.getWriter().write("{\"success\":true,\"id\":" + id + "}");
                }
            } else {
                // Create new server
                String sql = "INSERT INTO mcp_servers (name, transport_type, endpoint_url, " +
                             "command, command_args, auth_type, auth_config, tool_prefix, " +
                             "is_active, created_by) VALUES (?,?,?,?,?,?,?,?,?,?)";
                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name.trim());
                    ps.setString(2, transportType);
                    ps.setString(3, endpointUrl != null ? endpointUrl.trim() : null);
                    ps.setString(4, command != null ? command.trim() : null);
                    ps.setString(5, commandArgs);
                    ps.setString(6, authType);
                    ps.setString(7, CryptoUtil.encrypt(authConfig));
                    ps.setString(8, toolPrefix.trim());
                    ps.setBoolean(9, isActive);
                    ps.setInt(10, admin.getId());
                    ps.executeUpdate();

                    ResultSet keys = ps.getGeneratedKeys();
                    int newId = keys.next() ? keys.getInt(1) : 0;

                    McpClientManager.getInstance().reloadServerConfig(newId);
                    resp.getWriter().write("{\"success\":true,\"id\":" + newId + "}");
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"A server with that tool_prefix already exists\"}");
            } else {
                log("Error saving MCP server: " + e.getMessage());
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\":\"Database error\"}");
            }
        }
    }

    // ==================== Delete ====================

    /**
     * DELETE /api/mcp/admin/servers?id=N — delete an MCP server configuration.
     */
    private void handleDeleteServer(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String idStr = req.getParameter("id");
        if (idStr == null || idStr.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing id parameter\"}");
            return;
        }

        int id;
        try { id = Integer.parseInt(idStr); } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid id\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM mcp_servers WHERE id = ?")) {
            ps.setInt(1, id);
            int rows = ps.executeUpdate();

            McpClientManager.getInstance().removeServer(id);
            resp.getWriter().write("{\"success\":true,\"rowsAffected\":" + rows + "}");
        } catch (SQLException e) {
            log("Error deleting MCP server: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Test / Refresh ====================

    /**
     * POST /api/mcp/admin/servers/test?id=N — test connectivity by refreshing tools.
     */
    private void handleTestServer(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        int id = parseIdParam(req, resp);
        if (id <= 0) return;

        try {
            String toolsResponse = McpClientManager.getInstance().refreshServer(id);
            resp.getWriter().write("{\"success\":true,\"tools\":" + (toolsResponse != null ? toolsResponse : "null") + "}");
        } catch (IOException e) {
            // Update last_error in DB
            try (Connection conn = DBUtil.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "UPDATE mcp_servers SET last_error = ? WHERE id = ?")) {
                ps.setString(1, e.getMessage());
                ps.setInt(2, id);
                ps.executeUpdate();
            } catch (SQLException ex) {
                log("Error updating last_error: " + ex.getMessage());
            }

            resp.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            resp.getWriter().write("{\"error\":" + JsonUtil.quote("Connection failed: " + e.getMessage()) + "}");
        }
    }

    /**
     * POST /api/mcp/admin/servers/refresh?id=N — refresh cached tools for a server.
     */
    private void handleRefreshServer(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        int id = parseIdParam(req, resp);
        if (id <= 0) return;

        try {
            String toolsResponse = McpClientManager.getInstance().refreshServer(id);
            resp.getWriter().write("{\"success\":true,\"tools\":" + (toolsResponse != null ? toolsResponse : "null") + "}");
        } catch (IOException e) {
            // Update last_error in DB
            try (Connection conn = DBUtil.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "UPDATE mcp_servers SET last_error = ? WHERE id = ?")) {
                ps.setString(1, e.getMessage());
                ps.setInt(2, id);
                ps.executeUpdate();
            } catch (SQLException ex) {
                log("Error updating last_error: " + ex.getMessage());
            }

            resp.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            resp.getWriter().write("{\"error\":" + JsonUtil.quote("Refresh failed: " + e.getMessage()) + "}");
        }
    }

    // ==================== Tools ====================

    /**
     * GET /api/mcp/admin/servers/tools?id=N — list tools from a specific server.
     */
    private void handleGetTools(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        int id = parseIdParam(req, resp);
        if (id <= 0) return;

        // Try to get cached tools from the server config
        String cachedTools = null;
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT cached_tools FROM mcp_servers WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    cachedTools = rs.getString("cached_tools");
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    resp.getWriter().write("{\"error\":\"Server not found\"}");
                    return;
                }
            }
        } catch (SQLException e) {
            log("Error loading cached tools: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
            return;
        }

        // If no cached tools, try refreshing
        if (cachedTools == null || cachedTools.trim().isEmpty()) {
            try {
                cachedTools = McpClientManager.getInstance().refreshServer(id);
            } catch (IOException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
                resp.getWriter().write("{\"error\":" + JsonUtil.quote("Could not fetch tools: " + e.getMessage()) + "}");
                return;
            }
        }

        resp.getWriter().write("{\"tools\":" + (cachedTools != null ? cachedTools : "null") + "}");
    }

    // ==================== Audit Log ====================

    /**
     * GET /api/mcp/admin/audit — recent audit log entries.
     * Query param: limit (default 50)
     */
    private void handleGetAuditLog(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        int limit = 50;
        String limitStr = req.getParameter("limit");
        if (limitStr != null) {
            try {
                limit = Integer.parseInt(limitStr);
                if (limit <= 0 || limit > 1000) limit = 50;
            } catch (NumberFormatException e) {
                // keep default
            }
        }

        String auditJson = McpClientManager.getInstance().getAuditLog(limit);
        resp.getWriter().write(auditJson);
    }

    // ==================== Helpers ====================

    /**
     * Parse the "id" query parameter. Sends error response and returns 0 if invalid.
     */
    private int parseIdParam(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String idStr = req.getParameter("id");
        if (idStr == null || idStr.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing id parameter\"}");
            return 0;
        }

        try {
            int id = Integer.parseInt(idStr);
            if (id <= 0) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Invalid id\"}");
                return 0;
            }
            return id;
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid id\"}");
            return 0;
        }
    }

    // ==================== OAuth Authorization Code + PKCE ====================

    /**
     * POST /api/mcp/admin/servers/oauth/authorize?id=N
     * Initiates OAuth flow: builds authorization URL and returns it for browser redirect.
     */
    private void handleOAuthAuthorize(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        int id = parseIdParam(req, resp);
        if (id <= 0) return;

        // Load server config
        String endpointUrl = null;
        String authConfig = null;
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT endpoint_url, auth_config FROM mcp_servers WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    endpointUrl = rs.getString("endpoint_url");
                    authConfig = CryptoUtil.decrypt(rs.getString("auth_config"));
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    resp.getWriter().write("{\"error\":\"Server not found\"}");
                    return;
                }
            }
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
            return;
        }

        String clientId = authConfig != null ? JsonUtil.extractJsonString(authConfig, "clientId") : null;
        if (clientId == null || clientId.isEmpty()) clientId = "icDashBoard";

        // Build redirect URI for our callback
        String redirectUri = req.getScheme() + "://" + req.getServerName();
        int port = req.getServerPort();
        if (("http".equals(req.getScheme()) && port != 80) || ("https".equals(req.getScheme()) && port != 443)) {
            redirectUri += ":" + port;
        }
        redirectUri += req.getContextPath() + "/api/mcp/admin/oauth/callback";

        // State = serverId (to identify which server on callback)
        String state = String.valueOf(id);

        try {
            String[] result = HttpMcpConnection.buildAuthorizationUrl(endpointUrl, clientId, redirectUri, state);
            String authUrl = result[0];
            String codeVerifier = result[1];

            // Store code verifier in session for callback verification
            HttpSession session = req.getSession();
            session.setAttribute("mcp_oauth_verifier_" + id, codeVerifier);
            session.setAttribute("mcp_oauth_redirect_" + id, redirectUri);
            session.setAttribute("mcp_oauth_clientid_" + id, clientId);
            session.setAttribute("mcp_oauth_endpoint_" + id, endpointUrl);

            resp.getWriter().write("{\"authUrl\":" + JsonUtil.quote(authUrl) + "}");

        } catch (IOException e) {
            log.error("OAuth authorize error for server {}: {}", id, e.getMessage());
            resp.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            resp.getWriter().write("{\"error\":" + JsonUtil.quote("OAuth-fel: " + e.getMessage()) + "}");
        }
    }

    /**
     * GET /api/mcp/admin/oauth/callback?code=...&state=serverId
     * OAuth callback — exchanges auth code for tokens, stores refresh_token.
     * Redirects back to admin panel.
     */
    private void handleOAuthCallback(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String code = req.getParameter("code");
        String state = req.getParameter("state");
        String error = req.getParameter("error");

        if (error != null) {
            String desc = req.getParameter("error_description");
            resp.setContentType("text/html; charset=UTF-8");
            resp.getWriter().write("<html><body><h2>OAuth-fel</h2><p>" +
                    error + (desc != null ? ": " + desc : "") +
                    "</p><p><a href=\"" + req.getContextPath() + "/admin.jsp\">Tillbaka</a></p></body></html>");
            return;
        }

        if (code == null || state == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("text/html; charset=UTF-8");
            resp.getWriter().write("<html><body><h2>Saknar parametrar</h2>" +
                    "<p><a href=\"" + req.getContextPath() + "/admin.jsp\">Tillbaka</a></p></body></html>");
            return;
        }

        int serverId;
        try { serverId = Integer.parseInt(state); } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("Invalid state");
            return;
        }

        // Retrieve PKCE verifier from session
        HttpSession session = req.getSession(false);
        if (session == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("text/html; charset=UTF-8");
            resp.getWriter().write("<html><body><h2>Session utgången</h2>" +
                    "<p><a href=\"" + req.getContextPath() + "/admin.jsp\">Logga in igen</a></p></body></html>");
            return;
        }

        String codeVerifier = (String) session.getAttribute("mcp_oauth_verifier_" + serverId);
        String redirectUri = (String) session.getAttribute("mcp_oauth_redirect_" + serverId);
        String clientId = (String) session.getAttribute("mcp_oauth_clientid_" + serverId);
        String endpointUrl = (String) session.getAttribute("mcp_oauth_endpoint_" + serverId);

        if (codeVerifier == null || redirectUri == null || endpointUrl == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("text/html; charset=UTF-8");
            resp.getWriter().write("<html><body><h2>OAuth-session saknas</h2>" +
                    "<p>Försök auktorisera igen.</p>" +
                    "<p><a href=\"" + req.getContextPath() + "/admin.jsp\">Tillbaka</a></p></body></html>");
            return;
        }

        // Clean up session attributes
        session.removeAttribute("mcp_oauth_verifier_" + serverId);
        session.removeAttribute("mcp_oauth_redirect_" + serverId);
        session.removeAttribute("mcp_oauth_clientid_" + serverId);
        session.removeAttribute("mcp_oauth_endpoint_" + serverId);

        try {
            // Exchange code for tokens
            String tokenResponse = HttpMcpConnection.exchangeCodeForTokens(
                    endpointUrl, clientId, code, codeVerifier, redirectUri);

            String refreshToken = JsonUtil.extractJsonString(tokenResponse, "refresh_token");
            if (refreshToken == null || refreshToken.isEmpty()) {
                throw new IOException("Servern returnerade ingen refresh_token");
            }

            // Store refresh_token in auth_config (encrypted)
            String newAuthConfig = "{\"clientId\":\"" + clientId + "\",\"refreshToken\":\"" + refreshToken + "\"}";
            try (Connection conn = DBUtil.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                    "UPDATE mcp_servers SET auth_config = ?, last_error = NULL WHERE id = ?")) {
                ps.setString(1, CryptoUtil.encrypt(newAuthConfig));
                ps.setInt(2, serverId);
                ps.executeUpdate();
            }

            // Reload config and close old connection
            McpClientManager.getInstance().reloadServerConfig(serverId);

            log.info("OAuth authorization successful for MCP server {}", serverId);

            // Redirect back to admin page
            resp.sendRedirect(req.getContextPath() + "/admin.jsp?mcpAuth=success&serverId=" + serverId);

        } catch (Exception e) {
            log.error("OAuth callback error for server {}: {}", serverId, e.getMessage());

            // Update last_error
            try (Connection conn = DBUtil.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                    "UPDATE mcp_servers SET last_error = ? WHERE id = ?")) {
                ps.setString(1, "OAuth: " + e.getMessage());
                ps.setInt(2, serverId);
                ps.executeUpdate();
            } catch (SQLException ex) { /* ignore */ }

            resp.setContentType("text/html; charset=UTF-8");
            resp.getWriter().write("<html><body><h2>OAuth-fel</h2><p>" + e.getMessage() +
                    "</p><p><a href=\"" + req.getContextPath() + "/admin.jsp\">Tillbaka</a></p></body></html>");
        }
    }
}
