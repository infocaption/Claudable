package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.AdminUtil;
import com.infocaption.dashboard.util.AppConfig;
import com.infocaption.dashboard.util.AuditUtil;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.GroupUtil;
import com.infocaption.dashboard.util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin API Servlet — user management, group management, app config for admins only.
 *
 * GET  /api/admin/users          — List all users with admin status
 * POST /api/admin/users          — Toggle admin flag on a user
 * POST /api/admin/users/delete   — Delete a user
 * GET  /api/admin/groups         — List all groups with members + SSO mapping
 * POST /api/admin/groups         — Create or update a group
 * DELETE /api/admin/groups?id=N  — Delete a group
 * POST /api/admin/groups/members — Add/remove group member
 * GET  /api/admin/config         — List all config entries
 * POST /api/admin/config         — Update a config value
 * GET  /api/admin/widgets        — List all widgets
 * POST /api/admin/widgets        — Create or update a widget
 * DELETE /api/admin/widgets?id=N — Delete a widget
 */
public class AdminApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.equals("/users")) {
            handleListUsers(req, resp);
        } else if (pathInfo != null && pathInfo.equals("/groups")) {
            handleListGroups(req, resp);
        } else if (pathInfo != null && pathInfo.equals("/config")) {
            handleListConfig(req, resp);
        } else if (pathInfo != null && pathInfo.equals("/widgets")) {
            handleListWidgets(req, resp);
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
        if (pathInfo != null && pathInfo.equals("/users")) {
            handleToggleAdmin(req, resp, admin);
        } else if (pathInfo != null && pathInfo.equals("/groups")) {
            handleSaveGroup(req, resp, admin);
        } else if (pathInfo != null && pathInfo.equals("/groups/members")) {
            handleGroupMember(req, resp);
        } else if (pathInfo != null && pathInfo.equals("/config")) {
            handleUpdateConfig(req, resp, admin);
        } else if (pathInfo != null && pathInfo.equals("/widgets")) {
            handleSaveWidget(req, resp, admin);
        } else if (pathInfo != null && pathInfo.equals("/users/delete")) {
            handleDeleteUser(req, resp, admin);
        } else if (pathInfo != null && pathInfo.equals("/users/api-access")) {
            handleToggleApiAccess(req, resp, admin);
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
        if (pathInfo != null && pathInfo.equals("/widgets")) {
            handleDeleteWidget(req, resp);
        } else if (pathInfo != null && pathInfo.equals("/groups")) {
            handleDeleteGroup(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    /**
     * GET /api/admin/users — list all users with admin status.
     */
    private void handleListUsers(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String sql = "SELECT id, username, email, full_name, profile_picture_url, " +
                     "is_admin, has_api_access, is_active, last_login, created_at " +
                     "FROM users ORDER BY full_name, username";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            while (rs.next()) {
                if (!first) json.append(",");
                first = false;

                json.append("{");
                json.append("\"id\":").append(rs.getInt("id")).append(",");
                json.append("\"username\":").append(JsonUtil.quote(rs.getString("username"))).append(",");
                json.append("\"email\":").append(JsonUtil.quote(rs.getString("email"))).append(",");
                json.append("\"fullName\":").append(JsonUtil.quote(rs.getString("full_name"))).append(",");
                json.append("\"profilePictureUrl\":").append(JsonUtil.quote(rs.getString("profile_picture_url"))).append(",");
                json.append("\"isAdmin\":").append(rs.getBoolean("is_admin")).append(",");
                json.append("\"hasApiAccess\":").append(rs.getBoolean("has_api_access")).append(",");
                json.append("\"isActive\":").append(rs.getBoolean("is_active")).append(",");

                Timestamp lastLogin = rs.getTimestamp("last_login");
                json.append("\"lastLogin\":").append(JsonUtil.quote(lastLogin != null ? lastLogin.toString() : null)).append(",");

                Timestamp createdAt = rs.getTimestamp("created_at");
                json.append("\"createdAt\":").append(JsonUtil.quote(createdAt != null ? createdAt.toString() : null));

                json.append("}");
            }

            json.append("]");
            resp.getWriter().write(json.toString());

        } catch (SQLException e) {
            log("Error listing users: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    /**
     * POST /api/admin/users — toggle admin flag.
     * Body: {"userId": N, "isAdmin": true|false}
     */
    private void handleToggleAdmin(HttpServletRequest req, HttpServletResponse resp, User admin)
            throws IOException {

        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        String bodyStr = body.toString();

        int userId = JsonUtil.extractJsonInt(bodyStr, "userId");
        boolean isAdmin = JsonUtil.extractJsonBoolean(bodyStr, "isAdmin");

        if (userId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid userId\"}");
            return;
        }

        // Prevent self-demotion
        if (userId == admin.getId()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Cannot change own admin status\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE users SET is_admin = ? WHERE id = ?")) {
            ps.setBoolean(1, isAdmin);
            ps.setInt(2, userId);
            int rows = ps.executeUpdate();

            AuditUtil.logEvent(AuditUtil.ADMIN_ACTION, admin.getId(), req, "user", String.valueOf(userId),
                    "Set admin=" + isAdmin + " for userId=" + userId);

            resp.getWriter().write("{\"success\":true,\"rowsAffected\":" + rows + "}");
        } catch (SQLException e) {
            log("Error toggling admin: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Group Handlers ====================

    /**
     * GET /api/admin/groups — list all groups with member details + SSO mapping.
     * Returns JSON: { groups: [...], users: [...] }
     */
    private void handleListGroups(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        try (Connection conn = DBUtil.getConnection()) {
            int allaGroupId = GroupUtil.getAllaGroupId(conn);

            // Count all active users (for "Alla" group member count)
            int totalActiveUsers = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM users WHERE is_active = 1");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) totalActiveUsers = rs.getInt(1);
            }

            // Load all groups with member counts
            String groupSql = "SELECT g.id, g.name, g.icon, g.description, g.is_hidden, " +
                              "g.sso_department, g.created_by, g.created_at, " +
                              "(SELECT COUNT(*) FROM user_groups ug WHERE ug.group_id = g.id) AS member_count " +
                              "FROM `groups` g ORDER BY g.name";

            // Load all members grouped by group_id
            Map<Integer, List<int[]>> memberMap = new HashMap<>(); // groupId -> list of [userId]
            Map<Integer, String[]> userInfoMap = new HashMap<>();  // userId -> [fullName, email]

            String memberSql = "SELECT ug.group_id, ug.user_id, u.full_name, u.email " +
                               "FROM user_groups ug JOIN users u ON ug.user_id = u.id " +
                               "WHERE u.is_active = 1 ORDER BY u.full_name";
            try (PreparedStatement ps = conn.prepareStatement(memberSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int gId = rs.getInt("group_id");
                    int uId = rs.getInt("user_id");
                    memberMap.computeIfAbsent(gId, k -> new ArrayList<>()).add(new int[]{uId});
                    userInfoMap.put(uId, new String[]{rs.getString("full_name"), rs.getString("email")});
                }
            }

            // Build groups JSON array
            StringBuilder groupsJson = new StringBuilder("[");
            boolean first = true;
            try (PreparedStatement ps = conn.prepareStatement(groupSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (!first) groupsJson.append(",");
                    first = false;

                    int groupId = rs.getInt("id");
                    boolean isDefault = (groupId == allaGroupId);
                    int memberCount = isDefault ? totalActiveUsers : rs.getInt("member_count");

                    groupsJson.append("{");
                    groupsJson.append("\"id\":").append(groupId).append(",");
                    groupsJson.append("\"name\":").append(JsonUtil.quote(rs.getString("name"))).append(",");
                    groupsJson.append("\"icon\":").append(JsonUtil.quote(rs.getString("icon"))).append(",");
                    groupsJson.append("\"description\":").append(JsonUtil.quote(rs.getString("description"))).append(",");
                    groupsJson.append("\"isHidden\":").append(rs.getBoolean("is_hidden")).append(",");
                    groupsJson.append("\"ssoDepartment\":").append(JsonUtil.quote(rs.getString("sso_department"))).append(",");
                    groupsJson.append("\"isDefault\":").append(isDefault).append(",");
                    groupsJson.append("\"memberCount\":").append(memberCount).append(",");

                    Timestamp createdAt = rs.getTimestamp("created_at");
                    groupsJson.append("\"createdAt\":").append(JsonUtil.quote(createdAt != null ? createdAt.toString() : null)).append(",");

                    // Members array
                    groupsJson.append("\"members\":[");
                    List<int[]> members = memberMap.get(groupId);
                    if (members != null) {
                        boolean mFirst = true;
                        for (int[] m : members) {
                            if (!mFirst) groupsJson.append(",");
                            mFirst = false;
                            String[] info = userInfoMap.get(m[0]);
                            groupsJson.append("{\"id\":").append(m[0]).append(",");
                            groupsJson.append("\"fullName\":").append(JsonUtil.quote(info != null ? info[0] : null)).append(",");
                            groupsJson.append("\"email\":").append(JsonUtil.quote(info != null ? info[1] : null)).append("}");
                        }
                    }
                    groupsJson.append("]");

                    groupsJson.append("}");
                }
            }
            groupsJson.append("]");

            // Load all active users for add-member dropdown
            StringBuilder usersJson = new StringBuilder("[");
            String userSql = "SELECT id, full_name, email FROM users WHERE is_active = 1 ORDER BY full_name, email";
            try (PreparedStatement ps = conn.prepareStatement(userSql);
                 ResultSet rs = ps.executeQuery()) {
                boolean uFirst = true;
                while (rs.next()) {
                    if (!uFirst) usersJson.append(",");
                    uFirst = false;
                    usersJson.append("{\"id\":").append(rs.getInt("id")).append(",");
                    usersJson.append("\"fullName\":").append(JsonUtil.quote(rs.getString("full_name"))).append(",");
                    usersJson.append("\"email\":").append(JsonUtil.quote(rs.getString("email"))).append("}");
                }
            }
            usersJson.append("]");

            // Combine into response
            resp.getWriter().write("{\"groups\":" + groupsJson + ",\"users\":" + usersJson + "}");

        } catch (SQLException e) {
            log("Error listing groups: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    /**
     * POST /api/admin/groups — create or update a group.
     * Body: {"id": N (0 for new), "name": "...", "icon": "...", "description": "...",
     *        "isHidden": bool, "ssoDepartment": "..."|null}
     */
    private void handleSaveGroup(HttpServletRequest req, HttpServletResponse resp, User admin)
            throws IOException {

        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        String bodyStr = body.toString();

        int id = JsonUtil.extractJsonInt(bodyStr, "id");
        String name = JsonUtil.extractJsonString(bodyStr, "name");
        String icon = JsonUtil.extractJsonString(bodyStr, "icon");
        String description = JsonUtil.extractJsonString(bodyStr, "description");
        boolean isHidden = JsonUtil.extractJsonBoolean(bodyStr, "isHidden");
        String ssoDepartment = JsonUtil.extractJsonString(bodyStr, "ssoDepartment");

        if (name == null || name.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Namn krävs\"}");
            return;
        }

        if (icon == null || icon.trim().isEmpty()) icon = "\uD83D\uDC65"; // 👥

        // Normalize empty ssoDepartment to null
        if (ssoDepartment != null && ssoDepartment.trim().isEmpty()) ssoDepartment = null;

        try (Connection conn = DBUtil.getConnection()) {
            int allaGroupId = GroupUtil.getAllaGroupId(conn);

            if (id > 0) {
                // Update existing — protect "Alla" group
                if (id == allaGroupId) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"error\":\"Kan inte redigera standardgruppen Alla\"}");
                    return;
                }

                String sql = "UPDATE `groups` SET name=?, icon=?, description=?, is_hidden=?, sso_department=? WHERE id=?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, name.trim());
                    ps.setString(2, icon.trim());
                    ps.setString(3, description);
                    ps.setBoolean(4, isHidden);
                    ps.setString(5, ssoDepartment != null ? ssoDepartment.trim() : null);
                    ps.setInt(6, id);
                    ps.executeUpdate();

                    AuditUtil.logEvent(AuditUtil.GROUP_CHANGE, admin.getId(), req, "group", String.valueOf(id),
                            "Updated group: " + name.trim());

                    resp.getWriter().write("{\"success\":true,\"id\":" + id + "}");
                }
            } else {
                // Create new group
                String sql = "INSERT INTO `groups` (name, icon, description, is_hidden, sso_department, created_by) " +
                             "VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name.trim());
                    ps.setString(2, icon.trim());
                    ps.setString(3, description);
                    ps.setBoolean(4, isHidden);
                    ps.setString(5, ssoDepartment != null ? ssoDepartment.trim() : null);
                    ps.setInt(6, admin.getId());
                    ps.executeUpdate();

                    int newId = 0;
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        newId = keys.next() ? keys.getInt(1) : 0;
                    }

                    AuditUtil.logEvent(AuditUtil.GROUP_CHANGE, admin.getId(), req, "group", String.valueOf(newId),
                            "Created group: " + name.trim());

                    resp.getWriter().write("{\"success\":true,\"id\":" + newId + "}");
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"En grupp med det namnet finns redan\"}");
            } else {
                log("Error saving group: " + e.getMessage());
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\":\"Database error\"}");
            }
        }
    }

    /**
     * DELETE /api/admin/groups?id=N — delete a group.
     */
    private void handleDeleteGroup(HttpServletRequest req, HttpServletResponse resp)
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

        try (Connection conn = DBUtil.getConnection()) {
            int allaGroupId = GroupUtil.getAllaGroupId(conn);
            if (id == allaGroupId) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Kan inte ta bort standardgruppen Alla\"}");
                return;
            }

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM `groups` WHERE id = ?")) {
                ps.setInt(1, id);
                int rows = ps.executeUpdate();

                User adminUser = (User) req.getSession().getAttribute("user");
                AuditUtil.logEvent(AuditUtil.GROUP_CHANGE, adminUser != null ? adminUser.getId() : null, req, "group", String.valueOf(id),
                        "Deleted group id=" + id);

                resp.getWriter().write("{\"success\":true,\"rowsAffected\":" + rows + "}");
            }
        } catch (SQLException e) {
            log("Error deleting group: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    /**
     * POST /api/admin/groups/members — add or remove a member.
     * Body: {"groupId": N, "userId": N, "action": "add"|"remove"}
     */
    private void handleGroupMember(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        String bodyStr = body.toString();

        int groupId = JsonUtil.extractJsonInt(bodyStr, "groupId");
        int userId = JsonUtil.extractJsonInt(bodyStr, "userId");
        String action = JsonUtil.extractJsonString(bodyStr, "action");

        if (groupId <= 0 || userId <= 0 || action == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"groupId, userId and action required\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection()) {
            int allaGroupId = GroupUtil.getAllaGroupId(conn);
            if (groupId == allaGroupId) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Kan inte ändra medlemskap i Alla-gruppen\"}");
                return;
            }

            User adminUser = (User) req.getSession().getAttribute("user");

            if ("add".equals(action)) {
                String sql = "INSERT INTO user_groups (user_id, group_id) VALUES (?, ?) " +
                             "ON DUPLICATE KEY UPDATE user_id = user_id";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, userId);
                    ps.setInt(2, groupId);
                    ps.executeUpdate();

                    AuditUtil.logEvent(AuditUtil.GROUP_CHANGE, adminUser != null ? adminUser.getId() : null, req, "group", String.valueOf(groupId),
                            "Added userId=" + userId + " to groupId=" + groupId);

                    resp.getWriter().write("{\"success\":true}");
                }
            } else if ("remove".equals(action)) {
                String sql = "DELETE FROM user_groups WHERE user_id = ? AND group_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, userId);
                    ps.setInt(2, groupId);
                    ps.executeUpdate();

                    AuditUtil.logEvent(AuditUtil.GROUP_CHANGE, adminUser != null ? adminUser.getId() : null, req, "group", String.valueOf(groupId),
                            "Removed userId=" + userId + " from groupId=" + groupId);

                    resp.getWriter().write("{\"success\":true}");
                }
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Unknown action\"}");
            }
        } catch (SQLException e) {
            log("Error managing group member: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Config Handlers ====================

    /**
     * GET /api/admin/config — list all config entries.
     * Secret values are masked unless ?showSecrets=true.
     */
    private void handleListConfig(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String sql = "SELECT c.config_key, c.config_value, c.category, c.description, " +
                     "c.is_secret, c.updated_at, u.full_name AS updated_by_name " +
                     "FROM app_config c LEFT JOIN users u ON c.updated_by = u.id " +
                     "ORDER BY c.category, c.config_key";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            while (rs.next()) {
                if (!first) json.append(",");
                first = false;

                boolean isSecret = rs.getBoolean("is_secret");
                String value = rs.getString("config_value");

                json.append("{");
                json.append("\"key\":").append(JsonUtil.quote(rs.getString("config_key"))).append(",");
                json.append("\"value\":").append(JsonUtil.quote(isSecret ? maskValue(value) : value)).append(",");
                json.append("\"category\":").append(JsonUtil.quote(rs.getString("category"))).append(",");
                json.append("\"description\":").append(JsonUtil.quote(rs.getString("description"))).append(",");
                json.append("\"isSecret\":").append(isSecret).append(",");

                Timestamp updatedAt = rs.getTimestamp("updated_at");
                json.append("\"updatedAt\":").append(JsonUtil.quote(updatedAt != null ? updatedAt.toString() : null)).append(",");
                json.append("\"updatedByName\":").append(JsonUtil.quote(rs.getString("updated_by_name")));
                json.append("}");
            }

            json.append("]");
            resp.getWriter().write(json.toString());

        } catch (SQLException e) {
            log("Error listing config: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    /**
     * POST /api/admin/config — update a config value.
     * Body: {"key": "config.key", "value": "new-value"}
     */
    private void handleUpdateConfig(HttpServletRequest req, HttpServletResponse resp, User admin)
            throws IOException {

        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        String bodyStr = body.toString();

        String key = JsonUtil.extractJsonString(bodyStr, "key");
        String value = JsonUtil.extractJsonString(bodyStr, "value");

        if (key == null || key.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing config key\"}");
            return;
        }

        try {
            AppConfig.set(key.trim(), value, admin.getId());

            AuditUtil.logEvent(AuditUtil.CONFIG_CHANGE, admin.getId(), req, "config", key.trim(),
                    "Updated config key: " + key.trim());

            resp.getWriter().write("{\"success\":true}");
        } catch (SQLException e) {
            log("Error updating config: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    /**
     * Mask a secret value for display, showing only last 4 characters.
     */
    private String maskValue(String value) {
        if (value == null || value.length() <= 4) return "****";
        return "****" + value.substring(value.length() - 4);
    }

    // ==================== User Delete ====================

    /**
     * POST /api/admin/users/delete — delete a user.
     * Body: {"userId": N}
     */
    private void handleDeleteUser(HttpServletRequest req, HttpServletResponse resp, User admin)
            throws IOException {

        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        String bodyStr = body.toString();

        int userId = JsonUtil.extractJsonInt(bodyStr, "userId");
        if (userId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid userId\"}");
            return;
        }

        // Prevent self-deletion
        if (userId == admin.getId()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Cannot delete yourself\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection()) {
            // Delete from user_groups first (FK cascade should handle, but explicit is safer)
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM user_groups WHERE user_id = ?")) {
                ps.setInt(1, userId);
                ps.executeUpdate();
            }
            // Delete user
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
                ps.setInt(1, userId);
                int rows = ps.executeUpdate();

                AuditUtil.logEvent(AuditUtil.USER_DELETE, admin.getId(), req, "user", String.valueOf(userId),
                        "Deleted userId=" + userId);

                resp.getWriter().write("{\"success\":true,\"rowsAffected\":" + rows + "}");
            }
        } catch (SQLException e) {
            log("Error deleting user: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== API Access ====================

    /**
     * POST /api/admin/users/api-access — toggle API access flag.
     * Body: {"userId": N, "hasApiAccess": true|false}
     */
    private void handleToggleApiAccess(HttpServletRequest req, HttpServletResponse resp, User admin)
            throws IOException {

        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        String bodyStr = body.toString();

        int userId = JsonUtil.extractJsonInt(bodyStr, "userId");
        boolean hasApiAccess = JsonUtil.extractJsonBoolean(bodyStr, "hasApiAccess");

        if (userId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid userId\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection()) {
            // Update api access flag
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE users SET has_api_access = ? WHERE id = ?")) {
                ps.setBoolean(1, hasApiAccess);
                ps.setInt(2, userId);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    resp.getWriter().write("{\"error\":\"User not found\"}");
                    return;
                }
            }

            AuditUtil.logEvent(AuditUtil.ADMIN_ACTION, admin.getId(), req, "user", String.valueOf(userId),
                    "Set apiAccess=" + hasApiAccess + " for userId=" + userId);

            // If revoking access, also deactivate all user's tokens
            if (!hasApiAccess) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE api_tokens SET is_active = 0 WHERE user_id = ? AND is_active = 1")) {
                    ps.setInt(1, userId);
                    int revoked = ps.executeUpdate();
                    resp.getWriter().write("{\"success\":true,\"tokensRevoked\":" + revoked + "}");
                    return;
                }
            }

            resp.getWriter().write("{\"success\":true}");
        } catch (SQLException e) {
            log("Error toggling API access: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Widget Handlers ====================

    /**
     * GET /api/admin/widgets — list all widgets (including inactive) for admin management.
     */
    private void handleListWidgets(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String sql = "SELECT w.id, w.name, w.icon, w.description, w.render_key, " +
                     "w.custom_html, w.custom_js, w.refresh_seconds, w.is_active, w.created_by, " +
                     "u.full_name AS creator_name " +
                     "FROM widgets w LEFT JOIN users u ON w.created_by = u.id " +
                     "ORDER BY w.name";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            while (rs.next()) {
                if (!first) json.append(",");
                first = false;

                json.append("{");
                json.append("\"id\":").append(rs.getInt("id")).append(",");
                json.append("\"name\":").append(JsonUtil.quote(rs.getString("name"))).append(",");
                json.append("\"icon\":").append(JsonUtil.quote(rs.getString("icon"))).append(",");
                json.append("\"description\":").append(JsonUtil.quote(rs.getString("description"))).append(",");
                json.append("\"renderKey\":").append(JsonUtil.quote(rs.getString("render_key"))).append(",");
                json.append("\"customHtml\":").append(JsonUtil.quote(rs.getString("custom_html"))).append(",");
                json.append("\"customJs\":").append(JsonUtil.quote(rs.getString("custom_js"))).append(",");
                json.append("\"refreshSeconds\":").append(rs.getInt("refresh_seconds")).append(",");
                json.append("\"isActive\":").append(rs.getBoolean("is_active")).append(",");
                json.append("\"isCustom\":").append(rs.getString("custom_html") != null || rs.getString("custom_js") != null).append(",");
                json.append("\"creatorName\":").append(JsonUtil.quote(rs.getString("creator_name")));
                json.append("}");
            }

            json.append("]");
            resp.getWriter().write(json.toString());

        } catch (SQLException e) {
            log("Error listing widgets: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    /**
     * POST /api/admin/widgets — create or update a custom widget.
     * Body: {"id": N (optional), "name": "...", "icon": "...", "description": "...",
     *        "renderKey": "custom_...", "customHtml": "...", "customJs": "...",
     *        "refreshSeconds": N, "isActive": bool}
     */
    private void handleSaveWidget(HttpServletRequest req, HttpServletResponse resp, User admin)
            throws IOException {

        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        String bodyStr = body.toString();

        int id = JsonUtil.extractJsonInt(bodyStr, "id");
        String name = JsonUtil.extractJsonString(bodyStr, "name");
        String icon = JsonUtil.extractJsonString(bodyStr, "icon");
        String description = JsonUtil.extractJsonString(bodyStr, "description");
        String renderKey = JsonUtil.extractJsonString(bodyStr, "renderKey");
        String customHtml = JsonUtil.extractJsonString(bodyStr, "customHtml");
        String customJs = JsonUtil.extractJsonString(bodyStr, "customJs");
        int refreshSeconds = JsonUtil.extractJsonInt(bodyStr, "refreshSeconds");
        boolean isActive = JsonUtil.extractJsonBoolean(bodyStr, "isActive");

        if (name == null || name.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Name is required\"}");
            return;
        }

        if (icon == null || icon.trim().isEmpty()) icon = "\uD83D\uDCE6"; // 📦

        try (Connection conn = DBUtil.getConnection()) {
            if (id > 0) {
                // Update existing
                String sql = "UPDATE widgets SET name=?, icon=?, description=?, " +
                             "custom_html=?, custom_js=?, refresh_seconds=?, is_active=? WHERE id=?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, name.trim());
                    ps.setString(2, icon.trim());
                    ps.setString(3, description);
                    ps.setString(4, customHtml);
                    ps.setString(5, customJs);
                    ps.setInt(6, refreshSeconds);
                    ps.setBoolean(7, isActive);
                    ps.setInt(8, id);
                    ps.executeUpdate();

                    AuditUtil.logEvent(AuditUtil.MODULE_CHANGE, admin.getId(), req, "widget", String.valueOf(id),
                            "Updated widget: " + name.trim());

                    resp.getWriter().write("{\"success\":true,\"id\":" + id + "}");
                }
            } else {
                // Generate render_key if not provided
                if (renderKey == null || renderKey.trim().isEmpty()) {
                    renderKey = "custom_" + System.currentTimeMillis();
                }
                String sql = "INSERT INTO widgets (name, icon, description, render_key, " +
                             "custom_html, custom_js, refresh_seconds, is_active, created_by) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name.trim());
                    ps.setString(2, icon.trim());
                    ps.setString(3, description);
                    ps.setString(4, renderKey.trim());
                    ps.setString(5, customHtml);
                    ps.setString(6, customJs);
                    ps.setInt(7, refreshSeconds);
                    ps.setBoolean(8, isActive);
                    ps.setInt(9, admin.getId());
                    ps.executeUpdate();

                    int newId = 0;
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        newId = keys.next() ? keys.getInt(1) : 0;
                    }

                    AuditUtil.logEvent(AuditUtil.MODULE_CHANGE, admin.getId(), req, "widget", String.valueOf(newId),
                            "Created widget: " + name.trim());

                    resp.getWriter().write("{\"success\":true,\"id\":" + newId + "}");
                }
            }
        } catch (SQLException e) {
            log("Error saving widget: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    /**
     * DELETE /api/admin/widgets?id=N — delete a widget.
     */
    private void handleDeleteWidget(HttpServletRequest req, HttpServletResponse resp)
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
             PreparedStatement ps = conn.prepareStatement("DELETE FROM widgets WHERE id = ?")) {
            ps.setInt(1, id);
            int rows = ps.executeUpdate();

            User adminUser = (User) req.getSession().getAttribute("user");
            AuditUtil.logEvent(AuditUtil.MODULE_CHANGE, adminUser != null ? adminUser.getId() : null, req, "widget", String.valueOf(id),
                    "Deleted widget id=" + id);

            resp.getWriter().write("{\"success\":true,\"rowsAffected\":" + rows + "}");
        } catch (SQLException e) {
            log("Error deleting widget: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

}
