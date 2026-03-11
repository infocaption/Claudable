package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.DBUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /manage-users
 * Displays all active users in the system with group memberships
 * and Teams chat links.
 */
public class UserManageServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(UserManageServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        try (Connection conn = DBUtil.getConnection()) {

            // Load all active users
            List<Map<String, String>> users = loadAllUsers(conn);

            // Load group names per user
            Map<Integer, List<String>> userGroups = loadUserGroups(conn);

            req.setAttribute("users", users);
            req.setAttribute("userGroups", userGroups);

            req.getRequestDispatcher("/manage-users.jsp").forward(req, resp);

        } catch (SQLException e) {
            log.error("Failed to load user list", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
        }
    }

    /**
     * Load all active users ordered by full name.
     */
    private List<Map<String, String>> loadAllUsers(Connection conn) throws SQLException {
        List<Map<String, String>> users = new ArrayList<>();
        String sql = "SELECT id, username, full_name, email, profile_picture_url, last_login, created_at " +
                     "FROM users WHERE is_active = 1 ORDER BY full_name, username";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, String> u = new HashMap<>();
                u.put("id", String.valueOf(rs.getInt("id")));
                u.put("username", rs.getString("username"));
                u.put("fullName", rs.getString("full_name") != null ? rs.getString("full_name") : rs.getString("username"));
                u.put("email", rs.getString("email"));
                u.put("profilePictureUrl", rs.getString("profile_picture_url"));
                u.put("lastLogin", rs.getTimestamp("last_login") != null ? rs.getTimestamp("last_login").toString() : null);
                u.put("createdAt", rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : null);
                users.add(u);
            }
        }
        return users;
    }

    /**
     * Load group names per user (many-to-many).
     * Returns map of userId -> list of group names.
     */
    private Map<Integer, List<String>> loadUserGroups(Connection conn) throws SQLException {
        Map<Integer, List<String>> map = new HashMap<>();
        String sql = "SELECT ug.user_id, g.name, g.icon " +
                     "FROM user_groups ug " +
                     "JOIN `groups` g ON ug.group_id = g.id " +
                     "ORDER BY g.name";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int userId = rs.getInt("user_id");
                String groupName = rs.getString("name");
                String icon = rs.getString("icon");
                map.computeIfAbsent(userId, k -> new ArrayList<>()).add(icon + " " + groupName);
            }
        }
        return map;
    }
}
