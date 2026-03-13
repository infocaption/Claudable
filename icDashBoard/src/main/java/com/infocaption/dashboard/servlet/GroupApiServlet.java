package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.GroupUtil;
import com.infocaption.dashboard.util.JsonUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * GET  /api/groups  -> List all visible groups + membership status
 * POST /api/groups  -> Join or leave a group (JSON body: {"action":"join|leave","groupId":N})
 *
 * Visibility rules:
 * - Non-hidden groups are always shown
 * - Hidden groups are only shown if the user is already a member
 * - "Alla" group: everyone is implicitly a member (cannot leave)
 * - Hidden groups: cannot be joined via this API (must be added manually)
 */
public class GroupApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(GroupApiServlet.class);

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

        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        try (Connection conn = DBUtil.getConnection()) {

            // Get user's current group memberships
            Set<Integer> userGroupIds = GroupUtil.getGroupIdsForUser(conn, userId);
            int allaGroupId = GroupUtil.getAllaGroupId(conn);

            // Query all groups with member counts
            String sql = "SELECT g.id, g.name, g.icon, g.description, g.is_hidden, " +
                         "(SELECT COUNT(*) FROM user_groups ug WHERE ug.group_id = g.id) as member_count " +
                         "FROM `groups` g ORDER BY g.name";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {

                    StringBuilder json = new StringBuilder("[");
                    boolean first = true;

                    while (rs.next()) {
                        int groupId = rs.getInt("id");
                        String name = rs.getString("name");
                        String icon = rs.getString("icon");
                        String description = rs.getString("description");
                        boolean isHidden = rs.getBoolean("is_hidden");
                        int memberCount = rs.getInt("member_count");
                        boolean isMember = userGroupIds.contains(groupId);
                        boolean isDefault = (groupId == allaGroupId);

                        // "Alla" always has all users as members (implicit)
                        if (isDefault) {
                            // Count all active users for "Alla"
                            memberCount = countActiveUsers(conn);
                            isMember = true;
                        }

                        // Hidden groups: only show if user is a member
                        if (isHidden && !isMember) {
                            continue;
                        }

                        if (!first) json.append(",");
                        first = false;

                        json.append("{");
                        json.append("\"id\":").append(groupId).append(",");
                        json.append("\"name\":").append(JsonUtil.quote(name)).append(",");
                        json.append("\"icon\":").append(JsonUtil.quote(icon)).append(",");
                        json.append("\"description\":").append(JsonUtil.quote(description)).append(",");
                        json.append("\"isHidden\":").append(isHidden).append(",");
                        json.append("\"isMember\":").append(isMember).append(",");
                        json.append("\"memberCount\":").append(memberCount).append(",");
                        json.append("\"isDefault\":").append(isDefault);
                        json.append("}");
                    }

                    json.append("]");

                    PrintWriter out = resp.getWriter();
                    out.write(json.toString());
                    out.flush();
                }
            }

        } catch (SQLException e) {
            log.error("Failed to list groups", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
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

        resp.setContentType("application/json; charset=UTF-8");

        // Parse JSON body manually (no JSON library)
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }

        String bodyStr = body.toString();
        String action = JsonUtil.extractJsonString(bodyStr, "action");
        int groupId = JsonUtil.extractJsonInt(bodyStr, "groupId");

        if (action == null || groupId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing action or groupId\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection()) {

            int allaGroupId = GroupUtil.getAllaGroupId(conn);

            // Cannot join/leave "Alla" group
            if (groupId == allaGroupId) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Kan inte \u00e4ndra medlemskap i Alla-gruppen\"}");
                return;
            }

            // Check if group exists and get its hidden status
            boolean isHidden = false;
            boolean groupExists = false;
            String checkSql = "SELECT is_hidden FROM `groups` WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        groupExists = true;
                        isHidden = rs.getBoolean("is_hidden");
                    }
                }
            }

            if (!groupExists) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"Gruppen finns inte\"}");
                return;
            }

            if ("join".equals(action)) {
                // Cannot self-join hidden groups
                if (isHidden) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    resp.getWriter().write("{\"error\":\"Kan inte g\u00e5 med i dolda grupper. Kontakta en administrat\u00f6r.\"}");
                    return;
                }

                String insertSql = "INSERT INTO user_groups (user_id, group_id) VALUES (?, ?) " +
                                   "ON DUPLICATE KEY UPDATE user_id = user_id";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setInt(1, userId);
                    ps.setInt(2, groupId);
                    ps.executeUpdate();
                }

            } else if ("leave".equals(action)) {
                String deleteSql = "DELETE FROM user_groups WHERE user_id = ? AND group_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    ps.setInt(1, userId);
                    ps.setInt(2, groupId);
                    ps.executeUpdate();
                }

            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Unknown action\"}");
                return;
            }

            // Refresh session groups
            GroupUtil.refreshSessionGroups(session, userId);

            resp.getWriter().write("{\"success\":true}");

        } catch (SQLException e) {
            log.error("Failed to process group membership change for userId={}", userId, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private int countActiveUsers(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE is_active = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

}
