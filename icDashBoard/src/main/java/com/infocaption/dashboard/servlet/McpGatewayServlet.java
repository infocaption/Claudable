package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.AppConfig;
import com.infocaption.dashboard.util.JsonUtil;
import com.infocaption.dashboard.util.McpClientManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Gateway Servlet — JSON-RPC 2.0 endpoint that proxies requests to
 * upstream MCP servers via McpClientManager.
 *
 * POST /api/mcp — accepts JSON-RPC 2.0 requests with Bearer token auth.
 *
 * Supported methods:
 *   - initialize          — MCP protocol handshake
 *   - tools/list          — aggregated tool list from all upstream servers
 *   - tools/call          — route tool call to the correct upstream server
 *
 * Features:
 *   - Global on/off via AppConfig("mcp.enabled")
 *   - Per-user per-minute rate limiting
 *   - Argument size limit for tools/call
 *   - Audit logging for all tools/call invocations
 */
public class McpGatewayServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(McpGatewayServlet.class);

    // JSON-RPC 2.0 error codes
    private static final int ERR_PARSE       = -32700;
    private static final int ERR_INVALID_REQ = -32600;
    private static final int ERR_METHOD_NOT_FOUND = -32601;
    private static final int ERR_SERVER      = -32000;
    private static final int ERR_RATE_LIMITED = -32001;

    /** Rate limit state: userId -> [requestCount, windowStartMs] */
    private final ConcurrentHashMap<Integer, long[]> rateLimitMap = new ConcurrentHashMap<>();

    // ==================== Init / Destroy ====================

    @Override
    public void init() throws ServletException {
        McpClientManager.getInstance().initialize();
        log.info("McpGatewayServlet initialized");
    }

    @Override
    public void destroy() {
        McpClientManager.getInstance().shutdown();
        log.info("McpGatewayServlet destroyed");
    }

    // ==================== POST /api/mcp ====================

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        // 1. Check global MCP enabled flag
        if (!"true".equalsIgnoreCase(AppConfig.get("mcp.enabled", "true"))) {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            resp.getWriter().write("{\"error\":\"MCP gateway is disabled\"}");
            return;
        }

        // 2. Extract and validate Bearer token
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\":\"Missing or invalid Authorization header\"}");
            return;
        }

        String rawToken = authHeader.substring("Bearer ".length()).trim();
        User user = ApiTokenServlet.validateToken(rawToken);
        if (user == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\":\"Invalid or expired API token\"}");
            return;
        }

        // 3. Rate limit check
        int rateLimit = AppConfig.getInt("mcp.rateLimit.requestsPerMinute", 60);
        if (!checkRateLimit(user.getId(), rateLimit)) {
            resp.setStatus(429); // Too Many Requests
            resp.getWriter().write(jsonRpcError(null, ERR_RATE_LIMITED,
                    "Rate limit exceeded (" + rateLimit + " requests/minute)"));
            return;
        }

        // 4. Read request body
        String body = readBody(req);
        if (body == null || body.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(jsonRpcError(null, ERR_PARSE, "Empty request body"));
            return;
        }

        // 5. Parse JSON-RPC envelope
        String method = JsonUtil.extractJsonString(body, "method");
        int id = JsonUtil.extractJsonInt(body, "id");
        String jsonrpc = JsonUtil.extractJsonString(body, "jsonrpc");

        if (!"2.0".equals(jsonrpc) || method == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(jsonRpcError(id, ERR_INVALID_REQ,
                    "Invalid JSON-RPC 2.0 request (missing jsonrpc or method)"));
            return;
        }

        String params = JsonUtil.extractJsonObject(body, "params");

        // 6. Route to handler by method
        String result;
        switch (method) {
            case "initialize":
                result = handleInitialize(id);
                break;
            case "tools/list":
                result = handleToolsList(id);
                break;
            case "tools/call":
                result = handleToolsCall(id, params, user);
                break;
            default:
                resp.getWriter().write(jsonRpcError(id, ERR_METHOD_NOT_FOUND,
                        "Unknown method: " + method));
                return;
        }

        // 7. Return JSON-RPC response
        resp.getWriter().write(result);
    }

    // ==================== Method handlers ====================

    private String handleInitialize(int id) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"jsonrpc\":\"2.0\",\"id\":").append(id);
        sb.append(",\"result\":{");
        sb.append("\"protocolVersion\":\"2024-11-05\"");
        sb.append(",\"serverInfo\":{\"name\":\"icDashBoard MCP Gateway\",\"version\":\"1.0\"}");
        sb.append(",\"capabilities\":{\"tools\":{}}");
        sb.append("}}");
        return sb.toString();
    }

    private String handleToolsList(int id) {
        String tools = McpClientManager.getInstance().getAggregatedTools();

        StringBuilder sb = new StringBuilder();
        sb.append("{\"jsonrpc\":\"2.0\",\"id\":").append(id);
        sb.append(",\"result\":{\"tools\":").append(tools).append("}}");
        return sb.toString();
    }

    private String handleToolsCall(int id, String params, User user) {
        if (params == null) {
            return jsonRpcError(id, ERR_INVALID_REQ, "Missing params for tools/call");
        }

        String toolName = JsonUtil.extractJsonString(params, "name");
        if (toolName == null || toolName.isEmpty()) {
            return jsonRpcError(id, ERR_INVALID_REQ, "Missing params.name for tools/call");
        }

        // Validate tool name: must be alphanumeric, underscore, hyphen, or double underscore (prefix separator)
        if (!toolName.matches("^[a-zA-Z0-9_][a-zA-Z0-9_\\-]*$")) {
            return jsonRpcError(id, ERR_INVALID_REQ,
                    "Invalid tool name: must contain only alphanumeric, underscore, or hyphen characters");
        }

        // Extract arguments as raw JSON object
        String arguments = JsonUtil.extractJsonObject(params, "arguments");

        // Check argument size limit
        int maxArgSize = AppConfig.getInt("mcp.toolCallMaxArgs", 102400);
        if (arguments != null && arguments.length() > maxArgSize) {
            return jsonRpcError(id, ERR_INVALID_REQ,
                    "Arguments exceed maximum size (" + maxArgSize + " bytes)");
        }

        long startMs = System.currentTimeMillis();
        try {
            McpClientManager.ToolCallResult tcResult =
                    McpClientManager.getInstance().routeToolCall(toolName, arguments);

            long durationMs = System.currentTimeMillis() - startMs;

            // Audit log — success
            McpClientManager.getInstance().logAudit(
                    user.getId(), tcResult.serverId, "tools/call", toolName,
                    truncate(arguments, 500), "success", null, (int) durationMs);

            // Extract the "result" from the upstream JSON-RPC response and wrap it
            String upstreamResult = JsonUtil.extractJsonObject(tcResult.response, "result");
            if (upstreamResult != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("{\"jsonrpc\":\"2.0\",\"id\":").append(id);
                sb.append(",\"result\":").append(upstreamResult).append("}");
                return sb.toString();
            }

            // Check for upstream error
            String upstreamError = JsonUtil.extractJsonObject(tcResult.response, "error");
            if (upstreamError != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("{\"jsonrpc\":\"2.0\",\"id\":").append(id);
                sb.append(",\"error\":").append(upstreamError).append("}");
                return sb.toString();
            }

            // Fallback: pass through the raw response as the result
            StringBuilder sb = new StringBuilder();
            sb.append("{\"jsonrpc\":\"2.0\",\"id\":").append(id);
            sb.append(",\"result\":").append(JsonUtil.quote(tcResult.response)).append("}");
            return sb.toString();

        } catch (IOException e) {
            long durationMs = System.currentTimeMillis() - startMs;
            log.error("tools/call error for {}: {}", toolName, e.getMessage());

            // Audit log — error
            McpClientManager.getInstance().logAudit(
                    user.getId(), null, "tools/call", toolName,
                    truncate(arguments, 500), "error", e.getMessage(), (int) durationMs);

            return jsonRpcError(id, ERR_SERVER, "Tool call failed: " + e.getMessage());
        }
    }

    // ==================== Rate limiting ====================

    /**
     * Per-user sliding window rate limiter.
     * @return true if request is allowed, false if rate limit exceeded
     */
    private boolean checkRateLimit(int userId, int maxPerMinute) {
        long now = System.currentTimeMillis();
        long[] state = rateLimitMap.compute(userId, (key, existing) -> {
            if (existing == null || (now - existing[1]) > 60_000) {
                // New window
                return new long[]{1, now};
            }
            // Same window — increment
            existing[0]++;
            return existing;
        });
        return state[0] <= maxPerMinute;
    }

    // ==================== Helpers ====================

    private String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    /**
     * Build a JSON-RPC 2.0 error response.
     */
    private String jsonRpcError(Integer id, int code, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"jsonrpc\":\"2.0\",\"id\":");
        sb.append(id != null ? id : "null");
        sb.append(",\"error\":{\"code\":").append(code);
        sb.append(",\"message\":").append(JsonUtil.quote(message));
        sb.append("}}");
        return sb.toString();
    }

    /**
     * Truncate a string to the given max length for audit logging.
     */
    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
