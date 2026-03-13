package com.infocaption.dashboard.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpSession;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility for group membership management.
 * The "Alla" group is always included implicitly for every user.
 */
public class GroupUtil {
    private static final Logger log = LoggerFactory.getLogger(GroupUtil.class);

    /** Session attribute key for the user's group IDs */
    public static final String SESSION_GROUP_IDS = "userGroupIds";

    /**
     * Get all group IDs for a user, always including the "Alla" group.
     */
    public static Set<Integer> getGroupIdsForUser(Connection conn, int userId) throws SQLException {
        Set<Integer> groupIds = new HashSet<>();

        // Always include "Alla" group
        int allaId = getAllaGroupId(conn);
        if (allaId > 0) {
            groupIds.add(allaId);
        }

        // Add user's explicit group memberships
        String sql = "SELECT group_id FROM user_groups WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    groupIds.add(rs.getInt("group_id"));
                }
            }
        }

        return groupIds;
    }

    /**
     * Load user's group IDs and store them in the HTTP session.
     * Call this after login (both password and SSO).
     */
    public static void refreshSessionGroups(HttpSession session, int userId) {
        try (Connection conn = DBUtil.getConnection()) {
            Set<Integer> groupIds = getGroupIdsForUser(conn, userId);
            session.setAttribute(SESSION_GROUP_IDS, groupIds);
        } catch (SQLException e) {
            log.error("Failed to load group memberships for userId={}", userId, e);
            // Fallback: empty set (user will only see system + own private modules)
            session.setAttribute(SESSION_GROUP_IDS, new HashSet<Integer>());
        }
    }

    /**
     * Get the ID of the "Alla" group (the default group all users belong to).
     * Returns -1 if not found.
     */
    public static int getAllaGroupId(Connection conn) throws SQLException {
        String sql = "SELECT id FROM `groups` WHERE name = 'Alla' LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return -1;
    }

    /**
     * Get group IDs from session, with fallback.
     */
    @SuppressWarnings("unchecked")
    public static Set<Integer> getSessionGroupIds(HttpSession session) {
        Object obj = session.getAttribute(SESSION_GROUP_IDS);
        if (obj instanceof Set) {
            return (Set<Integer>) obj;
        }
        return new HashSet<>();
    }
}
