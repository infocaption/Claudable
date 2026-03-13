package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.GroupUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GET /api/modules
 * Returns a JSON array of all modules visible to the current user:
 * - System/shared modules with no group assignment → visible to everyone
 * - System/shared modules assigned to "Alla" group → visible to everyone
 * - System/shared modules assigned to specific groups → visible only to group members
 * - Private modules → visible only to the owner
 *
 * Group filtering applies equally to system and shared modules.
 * Sorted: system first, then shared, then private, then by name.
 */
public class ModuleApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(ModuleApiServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write("{\"error\":\"Not authenticated\"}");
            return;
        }

        User user = (User) session.getAttribute("user");
        int userId = user.getId();
        String ctxPath = req.getContextPath();

        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        // Query: return modules the user can see based on group membership
        // system modules are always visible
        // private modules visible to owner
        // shared modules visible if user is in at least one of the module's groups,
        // OR if the module is in the "Alla" group
        // Group filtering applies to ALL module types that have group assignments.
        // A module with NO group assignments is visible to everyone (system/shared default).
        // A module assigned ONLY to "Alla" is visible to everyone.
        // A module assigned to specific groups (not just "Alla") requires membership.
        String sql = "SELECT DISTINCT m.id, m.owner_user_id, m.module_type, m.name, m.icon, m.description, " +
                     "m.category, m.entry_file, m.directory_name, m.badge, m.version, m.ai_spec_text " +
                     "FROM modules m " +
                     "LEFT JOIN module_groups mg ON m.id = mg.module_id " +
                     "LEFT JOIN `groups` g ON mg.group_id = g.id " +
                     "WHERE m.is_active = 1 AND (" +
                     // Private modules: owner only
                     "  (m.module_type = 'private' AND m.owner_user_id = ?) " +
                     // System & shared: check group access
                     "  OR (m.module_type IN ('system', 'shared') AND (" +
                     // No group assignments at all → visible to everyone
                     "    mg.group_id IS NULL " +
                     // Assigned to "Alla" → visible to everyone
                     "    OR g.name = 'Alla' " +
                     // User is member of one of the module's groups
                     "    OR mg.group_id IN (SELECT ug.group_id FROM user_groups ug WHERE ug.user_id = ?)" +
                     "  ))" +
                     ") " +
                     "ORDER BY FIELD(m.module_type, 'system', 'shared', 'private'), m.name";

        try (Connection conn = DBUtil.getConnection()) {

            // First, get group names per module for the response
            Map<Integer, List<String>> moduleGroupNames = loadModuleGroupNames(conn);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.setInt(2, userId);
                try (ResultSet rs = ps.executeQuery()) {

                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;

                    int dbId = rs.getInt("id");
                    Integer ownerUserId = rs.getObject("owner_user_id") != null ? rs.getInt("owner_user_id") : null;
                    String moduleType = rs.getString("module_type");
                    String name = rs.getString("name");
                    String icon = rs.getString("icon");
                    String description = rs.getString("description");
                    String category = rs.getString("category");
                    String entryFile = rs.getString("entry_file");
                    String directoryName = rs.getString("directory_name");
                    String badge = rs.getString("badge");
                    String version = rs.getString("version");
                    String aiSpecText = rs.getString("ai_spec_text");
                    boolean hasAiSpec = aiSpecText != null && !aiSpecText.isEmpty();
                    boolean isOwner = ownerUserId != null && ownerUserId == userId;

                    String path = ctxPath + "/modules/" + directoryName + "/" + entryFile;

                    // Build groups JSON array
                    List<String> groups = moduleGroupNames.getOrDefault(dbId, new ArrayList<>());
                    StringBuilder groupsJson = new StringBuilder("[");
                    for (int i = 0; i < groups.size(); i++) {
                        if (i > 0) groupsJson.append(",");
                        groupsJson.append(quote(groups.get(i)));
                    }
                    groupsJson.append("]");

                    json.append("{");
                    json.append("\"id\":").append(quote(directoryName)).append(",");
                    json.append("\"dbId\":").append(dbId).append(",");
                    json.append("\"name\":").append(quote(name)).append(",");
                    json.append("\"icon\":").append(quote(icon)).append(",");
                    json.append("\"description\":").append(quote(description)).append(",");
                    json.append("\"category\":").append(quote(category)).append(",");
                    json.append("\"path\":").append(quote(path)).append(",");
                    json.append("\"badge\":").append(badge != null ? quote(badge) : "null").append(",");
                    json.append("\"version\":").append(quote(version)).append(",");
                    json.append("\"moduleType\":").append(quote(moduleType)).append(",");
                    json.append("\"isOwner\":").append(isOwner).append(",");
                    json.append("\"hasAiSpec\":").append(hasAiSpec).append(",");
                    json.append("\"aiSpecText\":").append(hasAiSpec ? quote(aiSpecText) : "null").append(",");
                    json.append("\"groups\":").append(groupsJson.toString());
                    json.append("}");
                }

                json.append("]");

                PrintWriter out = resp.getWriter();
                out.write(json.toString());
                out.flush();
                }
            }

        } catch (SQLException e) {
            log.error("Failed to load modules for user", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    /**
     * Load all module-group name mappings.
     */
    private Map<Integer, List<String>> loadModuleGroupNames(Connection conn) throws SQLException {
        Map<Integer, List<String>> map = new HashMap<>();
        String sql = "SELECT mg.module_id, g.name FROM module_groups mg " +
                     "JOIN `groups` g ON mg.group_id = g.id ORDER BY g.name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int moduleId = rs.getInt("module_id");
                    String groupName = rs.getString("name");
                    map.computeIfAbsent(moduleId, k -> new ArrayList<>()).add(groupName);
                }
            }
        }
        return map;
    }

    /**
     * JSON-escape a string and wrap in double quotes.
     */
    private static String quote(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
