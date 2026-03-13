package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.Group;
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
 * GET  /group/manage -> Show manage-groups.jsp (visible groups + membership)
 * POST /group/manage -> Handle actions (create, update, delete, add-member, remove-member)
 */
public class GroupManageServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(GroupManageServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        User user = (User) session.getAttribute("user");
        int userId = user.getId();

        try (Connection conn = DBUtil.getConnection()) {

            Set<Integer> userGroupIds = GroupUtil.getGroupIdsForUser(conn, userId);
            int allaGroupId = GroupUtil.getAllaGroupId(conn);

            // Load all groups with member counts
            List<Group> visibleGroups = new ArrayList<>();
            List<Group> myGroups = new ArrayList<>();

            String sql = "SELECT g.id, g.name, g.icon, g.description, g.is_hidden, g.created_by, g.created_at, " +
                         "(SELECT COUNT(*) FROM user_groups ug WHERE ug.group_id = g.id) as member_count " +
                         "FROM `groups` g ORDER BY g.name";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Group g = new Group();
                        g.setId(rs.getInt("id"));
                        g.setName(rs.getString("name"));
                        g.setIcon(rs.getString("icon"));
                        g.setDescription(rs.getString("description"));
                        g.setHidden(rs.getBoolean("is_hidden"));
                        g.setCreatedBy(rs.getObject("created_by") != null ? rs.getInt("created_by") : null);
                        g.setCreatedAt(rs.getTimestamp("created_at"));
                        g.setMemberCount(rs.getInt("member_count"));

                        boolean isMember = userGroupIds.contains(g.getId());
                        boolean isDefault = (g.getId() == allaGroupId);

                        if (isDefault) {
                            isMember = true;
                        }

                        g.setMember(isMember);

                        // Hidden groups only visible to members
                        if (g.isHidden() && !isMember) {
                            continue;
                        }

                        if (isMember) {
                            myGroups.add(g);
                        } else {
                            visibleGroups.add(g);
                        }
                    }
                }
            }

            // Load members per group for display
            Map<Integer, List<Map<String, String>>> groupMembers = loadGroupMembers(conn);

            req.setAttribute("myGroups", myGroups);
            req.setAttribute("visibleGroups", visibleGroups);
            req.setAttribute("groupMembers", groupMembers);
            req.setAttribute("allaGroupId", allaGroupId);

            // Load all users for add-member dropdown
            List<Map<String, String>> allUsers = loadAllUsers(conn);
            req.setAttribute("allUsers", allUsers);

        } catch (SQLException e) {
            log.error("Failed to load groups for management view", e);
            req.setAttribute("error", "Kunde inte h\u00e4mta grupper.");
        }

        req.getRequestDispatcher("/manage-groups.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        User user = (User) session.getAttribute("user");
        int userId = user.getId();

        String action = req.getParameter("action");

        if (action == null) {
            resp.sendRedirect(req.getContextPath() + "/group/manage?error=missing_action");
            return;
        }

        try (Connection conn = DBUtil.getConnection()) {

            int allaGroupId = GroupUtil.getAllaGroupId(conn);

            switch (action) {
                case "create": {
                    String name = req.getParameter("name");
                    String icon = req.getParameter("icon");
                    String description = req.getParameter("description");
                    String isHiddenStr = req.getParameter("isHidden");

                    if (name == null || name.trim().isEmpty()) {
                        resp.sendRedirect(req.getContextPath() + "/group/manage?error=name_required");
                        return;
                    }
                    if (icon == null || icon.trim().isEmpty()) icon = "\uD83D\uDC65"; // 👥

                    boolean isHidden = "on".equals(isHiddenStr) || "true".equals(isHiddenStr);

                    String sql = "INSERT INTO `groups` (name, icon, description, is_hidden, created_by) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, name.trim());
                        ps.setString(2, icon.trim());
                        ps.setString(3, description != null ? description.trim() : null);
                        ps.setBoolean(4, isHidden);
                        ps.setInt(5, userId);
                        ps.executeUpdate();
                    }
                    resp.sendRedirect(req.getContextPath() + "/group/manage?success=created");
                    break;
                }

                case "update": {
                    int groupId = Integer.parseInt(req.getParameter("groupId"));
                    String name = req.getParameter("name");
                    String icon = req.getParameter("icon");
                    String description = req.getParameter("description");
                    String isHiddenStr = req.getParameter("isHidden");

                    if (groupId == allaGroupId) {
                        resp.sendRedirect(req.getContextPath() + "/group/manage?error=cannot_edit_alla");
                        return;
                    }

                    boolean isHidden = "on".equals(isHiddenStr) || "true".equals(isHiddenStr);

                    String sql = "UPDATE `groups` SET name = ?, icon = ?, description = ?, is_hidden = ? WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, name.trim());
                        ps.setString(2, icon.trim());
                        ps.setString(3, description != null ? description.trim() : null);
                        ps.setBoolean(4, isHidden);
                        ps.setInt(5, groupId);
                        ps.executeUpdate();
                    }
                    resp.sendRedirect(req.getContextPath() + "/group/manage?success=updated");
                    break;
                }

                case "delete": {
                    int groupId = Integer.parseInt(req.getParameter("groupId"));
                    if (groupId == allaGroupId) {
                        resp.sendRedirect(req.getContextPath() + "/group/manage?error=cannot_delete_alla");
                        return;
                    }
                    // CASCADE deletes user_groups and module_groups entries
                    String sql = "DELETE FROM `groups` WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, groupId);
                        ps.executeUpdate();
                    }
                    resp.sendRedirect(req.getContextPath() + "/group/manage?success=deleted");
                    break;
                }

                case "add-member": {
                    int groupId = Integer.parseInt(req.getParameter("groupId"));
                    int targetUserId = Integer.parseInt(req.getParameter("userId"));
                    String sql = "INSERT INTO user_groups (user_id, group_id) VALUES (?, ?) " +
                                 "ON DUPLICATE KEY UPDATE user_id = user_id";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, targetUserId);
                        ps.setInt(2, groupId);
                        ps.executeUpdate();
                    }
                    resp.sendRedirect(req.getContextPath() + "/group/manage?success=member_added");
                    break;
                }

                case "remove-member": {
                    int groupId = Integer.parseInt(req.getParameter("groupId"));
                    int targetUserId = Integer.parseInt(req.getParameter("userId"));
                    if (groupId == allaGroupId) {
                        resp.sendRedirect(req.getContextPath() + "/group/manage?error=cannot_remove_from_alla");
                        return;
                    }
                    String sql = "DELETE FROM user_groups WHERE user_id = ? AND group_id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, targetUserId);
                        ps.setInt(2, groupId);
                        ps.executeUpdate();
                    }
                    resp.sendRedirect(req.getContextPath() + "/group/manage?success=member_removed");
                    break;
                }

                default:
                    resp.sendRedirect(req.getContextPath() + "/group/manage?error=unknown_action");
            }

        } catch (SQLException e) {
            log.error("Failed to process group management action '{}'", action, e);
            resp.sendRedirect(req.getContextPath() + "/group/manage?error=db_error");
        } catch (NumberFormatException e) {
            resp.sendRedirect(req.getContextPath() + "/group/manage?error=invalid_id");
        }
    }

    private Map<Integer, List<Map<String, String>>> loadGroupMembers(Connection conn) throws SQLException {
        Map<Integer, List<Map<String, String>>> map = new HashMap<>();
        String sql = "SELECT ug.group_id, u.id, u.full_name, u.email " +
                     "FROM user_groups ug JOIN users u ON ug.user_id = u.id " +
                     "WHERE u.is_active = 1 ORDER BY u.full_name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int groupId = rs.getInt("group_id");
                    Map<String, String> member = new HashMap<>();
                    member.put("id", String.valueOf(rs.getInt("id")));
                    member.put("fullName", rs.getString("full_name"));
                    member.put("email", rs.getString("email"));
                    map.computeIfAbsent(groupId, k -> new ArrayList<>()).add(member);
                }
            }
        }
        return map;
    }

    private List<Map<String, String>> loadAllUsers(Connection conn) throws SQLException {
        List<Map<String, String>> users = new ArrayList<>();
        String sql = "SELECT id, full_name, email FROM users WHERE is_active = 1 ORDER BY full_name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> u = new HashMap<>();
                    u.put("id", String.valueOf(rs.getInt("id")));
                    u.put("fullName", rs.getString("full_name"));
                    u.put("email", rs.getString("email"));
                    users.add(u);
                }
            }
        }
        return users;
    }
}
