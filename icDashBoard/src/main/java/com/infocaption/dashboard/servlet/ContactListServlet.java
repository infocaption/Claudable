package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.AppConfig;
import com.infocaption.dashboard.util.JsonUtil;
import com.infocaption.dashboard.util.SmartassicDBUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST API for querying contacts from the external smartassic database.
 * Used by the "Listor" tab in the Utskick module to build recipient lists.
 *
 * GET  /api/contacts/filters  -> Distinct filter option values (for dropdowns)
 * POST /api/contacts/query    -> Query contacts matching filter criteria
 *
 * External DB: smartassic (configured in app-secrets.properties, tables: zsocompany, zsocontacts, zsoserver)
 */
public class ContactListServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(ContactListServlet.class);

    private static int maxResults() {
        return AppConfig.getInt("contacts.maxResults", 5000);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = authenticate(req, resp);
        if (user == null) return;

        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        if (pathInfo.equals("/filters") || pathInfo.equals("/filters/")) {
            handleGetFilters(resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = authenticate(req, resp);
        if (user == null) return;

        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        if (pathInfo.equals("/query") || pathInfo.equals("/query/")) {
            handleQuery(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    // ==================== GET /api/contacts/filters ====================

    private void handleGetFilters(HttpServletResponse resp) throws IOException {
        try (Connection conn = SmartassicDBUtil.getConnection()) {
            StringBuilder json = new StringBuilder("{");

            json.append("\"serverUrls\":");
            appendDistinctValues(conn, json,
                "SELECT DISTINCT s.ServerUrl FROM zsoserver s " +
                "WHERE s.Active = 1 AND s.ServerUrl IS NOT NULL AND s.ServerUrl != '' " +
                "ORDER BY s.ServerUrl",
                "ServerUrl");

            json.append(",\"machineNames\":");
            appendDistinctValues(conn, json,
                "SELECT DISTINCT s.MachineName FROM zsoserver s " +
                "WHERE s.Active = 1 AND s.MachineName IS NOT NULL AND s.MachineName != '' " +
                "ORDER BY s.MachineName",
                "MachineName");

            json.append(",\"companyNames\":");
            appendDistinctValues(conn, json,
                "SELECT DISTINCT co.CompanyName FROM zsocompany co " +
                "WHERE co.CompanyName IS NOT NULL AND co.CompanyName != '' " +
                "ORDER BY co.CompanyName",
                "CompanyName");

            json.append(",\"companyCategories\":");
            appendDistinctIntValues(conn, json,
                "SELECT DISTINCT c.CompanyCategory FROM zsocontacts c " +
                "WHERE c.CompanyCategory IS NOT NULL " +
                "ORDER BY c.CompanyCategory",
                "CompanyCategory");

            json.append(",\"personCategories\":");
            appendDistinctIntValues(conn, json,
                "SELECT DISTINCT c.PersonCategory FROM zsocontacts c " +
                "WHERE c.PersonCategory IS NOT NULL " +
                "ORDER BY c.PersonCategory",
                "PersonCategory");

            json.append(",\"languages\":");
            appendDistinctValues(conn, json,
                "SELECT DISTINCT c.Language FROM zsocontacts c " +
                "WHERE c.Language IS NOT NULL AND c.Language != '' " +
                "ORDER BY c.Language",
                "Language");

            json.append("}");
            resp.getWriter().write(json.toString());

        } catch (SQLException e) {
            log.error("Failed to load contact filters from external database", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Kunde inte ansluta till kontaktdatabasen\"}");
        }
    }

    private void appendDistinctValues(Connection conn, StringBuilder json, String sql, String column)
            throws SQLException {
        json.append("[");
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            boolean first = true;
            while (rs.next()) {
                String val = rs.getString(column);
                if (val == null || val.trim().isEmpty()) continue;
                if (!first) json.append(",");
                first = false;
                json.append(JsonUtil.quote(val.trim()));
            }
        }
        json.append("]");
    }

    private void appendDistinctIntValues(Connection conn, StringBuilder json, String sql, String column)
            throws SQLException {
        json.append("[");
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            boolean first = true;
            while (rs.next()) {
                int val = rs.getInt(column);
                if (rs.wasNull()) continue;
                if (!first) json.append(",");
                first = false;
                json.append(val);
            }
        }
        json.append("]");
    }

    // ==================== POST /api/contacts/query ====================

    private void handleQuery(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readRequestBody(req);

        // Extract filter values from JSON
        List<String> serverUrls = JsonUtil.extractJsonStringList(body, "serverUrls");
        List<String> machineNames = JsonUtil.extractJsonStringList(body, "machineNames");
        String domain = JsonUtil.extractJsonString(body, "domain");
        List<String> companyNames = JsonUtil.extractJsonStringList(body, "companyNames");
        List<String> companyCategories = JsonUtil.extractJsonStringList(body, "companyCategories");
        List<String> personCategories = JsonUtil.extractJsonStringList(body, "personCategories");
        List<String> languages = JsonUtil.extractJsonStringList(body, "languages");

        // Build dynamic SQL
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        sql.append("SELECT DISTINCT c.PersonEmail, c.Language, c.CompanyCategory, ");
        sql.append("c.PersonCategory, co.CompanyName, s.ServerUrl, s.MachineName ");
        sql.append("FROM zsocontacts c ");
        sql.append("JOIN zsocompany co ON co.CompanyId = c.CompanyId ");

        // Use INNER JOIN when server filters are active, LEFT JOIN otherwise
        boolean hasServerFilter = !isEmpty(serverUrls) || !isEmpty(machineNames);
        if (hasServerFilter) {
            sql.append("JOIN zsoserver s ON s.CompanyId = c.CompanyId AND s.Active = 1 ");
        } else {
            sql.append("LEFT JOIN zsoserver s ON s.CompanyId = c.CompanyId AND s.Active = 1 ");
        }

        sql.append("WHERE c.PersonEmail IS NOT NULL AND c.PersonEmail != '' ");
        sql.append("AND (c.Retired = 0 OR c.Retired IS NULL) ");

        // ServerUrl filter
        if (!isEmpty(serverUrls)) {
            sql.append("AND s.ServerUrl IN (");
            appendPlaceholders(sql, params, serverUrls);
            sql.append(") ");
        }

        // MachineName filter
        if (!isEmpty(machineNames)) {
            sql.append("AND s.MachineName IN (");
            appendPlaceholders(sql, params, machineNames);
            sql.append(") ");
        }

        // Domain filter (freetext LIKE match on server URL or email domain)
        if (domain != null && !domain.trim().isEmpty()) {
            sql.append("AND (s.ServerUrl LIKE ? OR c.PersonEmail LIKE ?) ");
            params.add("%" + domain.trim() + "%");
            params.add("%@" + domain.trim() + "%");
        }

        // CompanyName filter
        if (!isEmpty(companyNames)) {
            sql.append("AND co.CompanyName IN (");
            appendPlaceholders(sql, params, companyNames);
            sql.append(") ");
        }

        // CompanyCategory filter
        if (!isEmpty(companyCategories)) {
            sql.append("AND c.CompanyCategory IN (");
            appendIntPlaceholders(sql, params, companyCategories);
            sql.append(") ");
        }

        // PersonCategory filter
        if (!isEmpty(personCategories)) {
            sql.append("AND c.PersonCategory IN (");
            appendIntPlaceholders(sql, params, personCategories);
            sql.append(") ");
        }

        // Language filter
        if (!isEmpty(languages)) {
            sql.append("AND c.Language IN (");
            appendPlaceholders(sql, params, languages);
            sql.append(") ");
        }

        sql.append("ORDER BY c.PersonEmail LIMIT ").append(maxResults());

        // Execute query
        try (Connection conn = SmartassicDBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {

                // Collect all data per email, aggregating multiple servers
                // LinkedHashMap preserves insertion order
                Map<String, String[]> contactData = new LinkedHashMap<>(); // email -> [companyName, language, compCat, persCat]
                Map<String, Set<String>> contactServers = new LinkedHashMap<>(); // email -> set of serverUrls
                Map<String, Set<String>> contactMachines = new LinkedHashMap<>(); // email -> set of machineNames
                int totalRows = 0;

                while (rs.next()) {
                    totalRows++;
                    String email = rs.getString("PersonEmail");
                    if (email == null || email.trim().isEmpty()) continue;
                    email = email.trim().toLowerCase();

                    // Store contact data (first occurrence wins for non-server fields)
                    if (!contactData.containsKey(email)) {
                        contactData.put(email, new String[]{
                            rs.getString("CompanyName"),
                            rs.getString("Language"),
                            nullSafeInt(rs, "CompanyCategory"),
                            nullSafeInt(rs, "PersonCategory")
                        });
                        contactServers.put(email, new LinkedHashSet<>());
                        contactMachines.put(email, new LinkedHashSet<>());
                    }

                    // Aggregate ALL servers for this contact
                    String serverUrl = rs.getString("ServerUrl");
                    if (serverUrl != null && !serverUrl.trim().isEmpty()) {
                        contactServers.get(email).add(serverUrl.trim());
                    }
                    String machineName = rs.getString("MachineName");
                    if (machineName != null && !machineName.trim().isEmpty()) {
                        contactMachines.get(email).add(machineName.trim());
                    }
                }

                // Build JSON response
                StringBuilder json = new StringBuilder("{");
                json.append("\"contacts\":[");

                boolean first = true;
                for (Map.Entry<String, String[]> entry : contactData.entrySet()) {
                    if (!first) json.append(",");
                    first = false;

                    String email = entry.getKey();
                    String[] data = entry.getValue();
                    Set<String> servers = contactServers.get(email);
                    Set<String> machines = contactMachines.get(email);

                    json.append("{");
                    json.append("\"email\":").append(JsonUtil.quote(email)).append(",");
                    json.append("\"companyName\":").append(JsonUtil.quote(data[0])).append(",");
                    json.append("\"language\":").append(JsonUtil.quote(data[1])).append(",");
                    json.append("\"companyCategory\":").append(data[2]).append(",");
                    json.append("\"personCategory\":").append(data[3]).append(",");
                    // serverUrl: comma-separated if multiple, for backward compat
                    json.append("\"serverUrl\":").append(JsonUtil.quote(String.join(", ", servers))).append(",");
                    json.append("\"machineName\":").append(JsonUtil.quote(String.join(", ", machines))).append(",");
                    // serverUrls: array for template variable support (per-server sending)
                    json.append("\"serverUrls\":[");
                    boolean sf = true;
                    for (String s : servers) {
                        if (!sf) json.append(",");
                        sf = false;
                        json.append(JsonUtil.quote(s));
                    }
                    json.append("]");
                    json.append("}");
                }

                json.append("],");
                json.append("\"emailCount\":").append(contactData.size()).append(",");
                json.append("\"totalRows\":").append(totalRows).append(",");
                json.append("\"maxResults\":").append(maxResults()).append(",");
                json.append("\"limited\":").append(totalRows >= maxResults());
                json.append("}");

                resp.getWriter().write(json.toString());
            }

        } catch (SQLException e) {
            log.error("Failed to query contacts from external database", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Databasfel\"}");
        }
    }

    // ==================== Utility Methods ====================

    private User authenticate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write("{\"error\":\"Not authenticated\"}");
            return null;
        }
        User user = (User) session.getAttribute("user");
        // Contact list contains sensitive customer data — restrict to admin users
        if (!user.isAdmin()) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write("{\"error\":\"Admin access required\"}");
            return null;
        }
        return user;
    }

    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private boolean isEmpty(List<String> list) {
        return list == null || list.isEmpty();
    }

    private void appendPlaceholders(StringBuilder sql, List<Object> params, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("?");
            params.add(values.get(i));
        }
    }

    private void appendIntPlaceholders(StringBuilder sql, List<Object> params, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("?");
            try {
                params.add(Integer.parseInt(values.get(i).trim()));
            } catch (NumberFormatException e) {
                params.add(values.get(i));
            }
        }
    }

    private String nullSafeInt(ResultSet rs, String column) throws SQLException {
        int val = rs.getInt(column);
        if (rs.wasNull()) return "null";
        return String.valueOf(val);
    }

}
