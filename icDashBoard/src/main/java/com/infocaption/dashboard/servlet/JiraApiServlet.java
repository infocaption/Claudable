package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.AppConfig;
import com.infocaption.dashboard.util.CryptoUtil;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Jira API proxy servlet.
 *
 * GET /api/jira/issues  — Fetch active issues assigned to current user
 * GET /api/jira/status  — Test Jira connectivity and configuration
 *
 * Per-user Jira configuration read from user_preferences:
 *   jira.domain, jira.email, jira.apiToken
 */
public class JiraApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final String PREF_DOMAIN = "jira.domain";
    private static final String PREF_EMAIL = "jira.email";
    private static final String PREF_TOKEN = "jira.apiToken";

    private HttpClient httpClient;

    @Override
    public void init() throws ServletException {
        int timeout = AppConfig.getInt("jira.connectTimeout", 15);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        log("JiraApiServlet initialized with " + timeout + "s timeout");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendError(resp, 401, "Not authenticated");
            return;
        }

        User user = (User) session.getAttribute("user");
        String pathInfo = req.getPathInfo();

        if ("/issues".equals(pathInfo)) {
            handleFetchIssues(user.getId(), resp);
        } else if ("/status".equals(pathInfo)) {
            handleCheckStatus(user.getId(), resp);
        } else {
            sendError(resp, 404, "Not found");
        }
    }

    // ── GET /api/jira/issues ─────────────────────────────────────────────

    private void handleFetchIssues(int userId, HttpServletResponse resp) throws IOException {
        JiraConfig cfg = loadJiraConfig(userId);
        if (!cfg.isConfigured()) {
            sendError(resp, 400, cfg.getError());
            return;
        }

        String jql = URLEncoder.encode(
                "assignee=currentUser() AND status!=Done ORDER BY updated DESC",
                StandardCharsets.UTF_8);
        String fields = "key,summary,status,priority,assignee,created,updated,project";
        String url = "https://" + cfg.domain + "/rest/api/3/search?jql=" + jql
                + "&maxResults=50&fields=" + fields;

        try {
            int timeout = AppConfig.getInt("jira.connectTimeout", 15);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Authorization", basicAuth(cfg.email, cfg.apiToken))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> jiraResp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (jiraResp.statusCode() != 200) {
                sendError(resp, 502, "Jira returnerade HTTP " + jiraResp.statusCode());
                return;
            }

            String simplified = simplifyIssuesResponse(jiraResp.body(), cfg.domain);
            resp.getWriter().write(simplified);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendError(resp, 504, "Timeout vid anslutning till Jira");
        } catch (Exception e) {
            sendError(resp, 500, "Kunde inte h\u00e4mta Jira-\u00e4renden: " + e.getMessage());
        }
    }

    // ── GET /api/jira/status ─────────────────────────────────────────────

    private void handleCheckStatus(int userId, HttpServletResponse resp) throws IOException {
        JiraConfig cfg = loadJiraConfig(userId);
        if (!cfg.isConfigured()) {
            resp.getWriter().write("{\"configured\":false,\"error\":" + JsonUtil.quote(cfg.getError()) + "}");
            return;
        }

        String url = "https://" + cfg.domain + "/rest/api/3/myself";
        try {
            int timeout = AppConfig.getInt("jira.connectTimeout", 15);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Authorization", basicAuth(cfg.email, cfg.apiToken))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> jiraResp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (jiraResp.statusCode() == 200) {
                String displayName = JsonUtil.extractJsonString(jiraResp.body(), "displayName");
                resp.getWriter().write("{\"configured\":true,\"reachable\":true,\"displayName\":"
                        + JsonUtil.quote(displayName != null ? displayName : "ok") + "}");
            } else {
                resp.getWriter().write("{\"configured\":true,\"reachable\":false,\"error\":\"HTTP "
                        + jiraResp.statusCode() + "\"}");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            resp.getWriter().write("{\"configured\":true,\"reachable\":false,\"error\":\"Timeout\"}");
        } catch (Exception e) {
            resp.getWriter().write("{\"configured\":true,\"reachable\":false,\"error\":"
                    + JsonUtil.quote(e.getMessage()) + "}");
        }
    }

    // ── Load per-user Jira config from user_preferences ──────────────────

    private JiraConfig loadJiraConfig(int userId) {
        String domain = null, email = null, apiToken = null;
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT pref_key, pref_value FROM user_preferences " +
                     "WHERE user_id = ? AND pref_key IN (?, ?, ?)")) {
            ps.setInt(1, userId);
            ps.setString(2, PREF_DOMAIN);
            ps.setString(3, PREF_EMAIL);
            ps.setString(4, PREF_TOKEN);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("pref_key");
                    String val = rs.getString("pref_value");
                    if (PREF_DOMAIN.equals(key)) domain = val;
                    else if (PREF_EMAIL.equals(key)) email = val;
                    else if (PREF_TOKEN.equals(key)) apiToken = CryptoUtil.decrypt(val);
                }
            }
        } catch (SQLException e) {
            log("Failed to load Jira config for userId=" + userId + ": " + e.getMessage());
        }
        return new JiraConfig(domain, email, apiToken);
    }

    // ── Simplify Jira search response ────────────────────────────────────

    private String simplifyIssuesResponse(String jiraJson, String domain) {
        String issuesContent = JsonUtil.extractJsonArray(jiraJson, "issues");
        if (issuesContent == null) {
            return "{\"issues\":[],\"total\":0}";
        }

        List<String> objects = splitJsonObjects(issuesContent);
        StringBuilder sb = new StringBuilder("{\"issues\":[");
        boolean first = true;

        for (String issueJson : objects) {
            if (!first) sb.append(",");
            first = false;

            String key = JsonUtil.extractJsonString(issueJson, "key");
            String fieldsJson = JsonUtil.extractJsonObject(issueJson, "fields");

            String summary = fieldsJson != null ? JsonUtil.extractJsonString(fieldsJson, "summary") : null;
            String statusJson = fieldsJson != null ? JsonUtil.extractJsonObject(fieldsJson, "status") : null;
            String statusName = statusJson != null ? JsonUtil.extractJsonString(statusJson, "name") : null;
            String priorityJson = fieldsJson != null ? JsonUtil.extractJsonObject(fieldsJson, "priority") : null;
            String priorityName = priorityJson != null ? JsonUtil.extractJsonString(priorityJson, "name") : null;
            String projectJson = fieldsJson != null ? JsonUtil.extractJsonObject(fieldsJson, "project") : null;
            String projectKey = projectJson != null ? JsonUtil.extractJsonString(projectJson, "key") : null;
            String updated = fieldsJson != null ? JsonUtil.extractJsonString(fieldsJson, "updated") : null;

            String issueUrl = "https://" + domain + "/browse/" + (key != null ? key : "");

            sb.append("{");
            sb.append("\"key\":").append(JsonUtil.quote(key != null ? key : "")).append(",");
            sb.append("\"summary\":").append(JsonUtil.quote(summary != null ? summary : "")).append(",");
            sb.append("\"status\":").append(JsonUtil.quote(statusName != null ? statusName : "")).append(",");
            sb.append("\"priority\":").append(JsonUtil.quote(priorityName != null ? priorityName : "")).append(",");
            sb.append("\"projectKey\":").append(JsonUtil.quote(projectKey != null ? projectKey : "")).append(",");
            sb.append("\"updated\":").append(JsonUtil.quote(updated != null ? updated : "")).append(",");
            sb.append("\"url\":").append(JsonUtil.quote(issueUrl));
            sb.append("}");
        }

        sb.append("],\"total\":").append(objects.size()).append("}");
        return sb.toString();
    }

    private static List<String> splitJsonObjects(String arrayContent) {
        List<String> objects = new ArrayList<>();
        if (arrayContent == null || arrayContent.trim().isEmpty()) return objects;

        int depth = 0;
        int start = 0;
        boolean inString = false;

        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if (inString) {
                if (c == '\\') { i++; }
                else if (c == '"') { inString = false; }
            } else {
                if (c == '"') { inString = true; }
                else if (c == '{') {
                    if (depth == 0) start = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        objects.add(arrayContent.substring(start, i + 1));
                    }
                }
            }
        }
        return objects;
    }

    // ── Utilities ────────────────────────────────────────────────────────

    private static String basicAuth(String email, String apiToken) {
        String cred = email + ":" + apiToken;
        return "Basic " + Base64.getEncoder().encodeToString(cred.getBytes(StandardCharsets.UTF_8));
    }

    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.getWriter().write("{\"error\":" + JsonUtil.quote(message) + "}");
    }

    // ── Inner config holder ──────────────────────────────────────────────

    private static class JiraConfig {
        final String domain;
        final String email;
        final String apiToken;

        JiraConfig(String domain, String email, String apiToken) {
            this.domain = domain;
            this.email = email;
            this.apiToken = apiToken;
        }

        boolean isConfigured() {
            return domain != null && !domain.isEmpty()
                    && email != null && !email.isEmpty()
                    && apiToken != null && !apiToken.isEmpty();
        }

        String getError() {
            if (domain == null || domain.isEmpty()) return "Jira-dom\u00e4n \u00e4r inte konfigurerad. G\u00e5 till Inst\u00e4llningar.";
            if (email == null || email.isEmpty()) return "Jira-e-post \u00e4r inte konfigurerad. G\u00e5 till Inst\u00e4llningar.";
            if (apiToken == null || apiToken.isEmpty()) return "Jira API-token \u00e4r inte konfigurerad. G\u00e5 till Inst\u00e4llningar.";
            return null;
        }
    }
}
