package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.filter.CsrfFilter;
import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.AppConfig;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.GroupUtil;
import com.infocaption.dashboard.util.SamlConfigUtil;
import com.onelogin.saml2.Auth;
import com.onelogin.saml2.settings.Saml2Settings;

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
import java.util.Collection;
import java.util.List;

/**
 * SAML2 Assertion Consumer Service (ACS) Servlet.
 * POST /saml/acs — Receives the SAML Response from the IdP, validates it,
 * creates/finds the user in the database, and establishes a session.
 */
public class SamlAcsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(SamlAcsServlet.class);

    /**
     * Placeholder password for SSO users. This value can never be matched
     * by BCrypt.checkpw(), so SSO users cannot log in with username/password.
     */
    private static final String SSO_PASSWORD_PLACEHOLDER = "SSO_NO_PASSWORD";

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String contextPath = req.getContextPath();

        try {
            Saml2Settings settings = SamlConfigUtil.getSaml2Settings(getServletContext());
            Auth auth = new Auth(settings, req, resp);
            auth.processResponse();

            List<String> errors = auth.getErrors();
            if (!errors.isEmpty()) {
                log.error("SAML ACS errors: {}", errors);
                String reason = auth.getLastErrorReason();
                if (reason != null && !reason.isEmpty()) {
                    log.error("SAML last error reason: {}", reason);
                }
                resp.sendRedirect(contextPath + "/login?error=sso");
                return;
            }

            if (!auth.isAuthenticated()) {
                log.warn("SAML ACS: user not authenticated");
                resp.sendRedirect(contextPath + "/login?error=sso");
                return;
            }

            // Extract user info from SAML assertion
            // NameID is configured as user.userprincipalname in Azure
            String nameId = auth.getNameId();

            // Claim URIs — all configurable via app_config (Admin → Inställningar)
            String emailClaimUri = AppConfig.get("sso.emailClaimUri",
                    "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress");
            String givenNameClaimUri = AppConfig.get("sso.givenNameClaimUri",
                    "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname");
            String surnameClaimUri = AppConfig.get("sso.surnameClaimUri",
                    "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname");
            String nameClaimUri = AppConfig.get("sso.nameClaimUri",
                    "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name");
            String displayNameClaimUri = AppConfig.get("sso.displayNameClaimUri",
                    "http://schemas.microsoft.com/identity/claims/displayname");

            // Try to get email from configured claim first, fall back to NameID
            String email = getAttributeValue(auth, emailClaimUri);
            if (email == null || email.isEmpty()) {
                email = nameId; // UPN is typically same as email
            }

            // Build display name from givenname + surname claims
            String givenName = getAttributeValue(auth, givenNameClaimUri);
            String surname = getAttributeValue(auth, surnameClaimUri);
            String displayName = null;
            if (givenName != null && !givenName.isEmpty() && surname != null && !surname.isEmpty()) {
                displayName = givenName + " " + surname;
            } else if (givenName != null && !givenName.isEmpty()) {
                displayName = givenName;
            }

            // Fallback: try 'name' claim
            if (displayName == null || displayName.isEmpty()) {
                displayName = getAttributeValue(auth, nameClaimUri);
            }
            // Fallback: try displayname claim
            if (displayName == null || displayName.isEmpty()) {
                displayName = getAttributeValue(auth, displayNameClaimUri);
            }
            // Last resort: use email prefix
            if (displayName == null || displayName.isEmpty()) {
                displayName = email.contains("@") ? email.substring(0, email.indexOf("@")) : email;
            }

            log.info("SAML ACS: NameID={}, email={}, displayName={}", nameId, email, displayName);

            // Find or create user in database
            User user = findOrCreateSsoUser(email, displayName);
            if (user == null) {
                log.error("SAML ACS: failed to find/create user for {}", email);
                resp.sendRedirect(contextPath + "/login?error=sso");
                return;
            }

            // SSO department-based group auto-assignment
            boolean autoAssign = AppConfig.getBoolean("sso.autoAssignGroups", true);
            if (autoAssign) {
                String deptClaimUri = AppConfig.get("sso.departmentClaimUri",
                        "department/department");
                String department = getAttributeValue(auth, deptClaimUri);
                log.info("SAML ACS: department claim value = {}", department != null ? "[" + department + "]" : "null");
                if (department != null && !department.trim().isEmpty()) {
                    // Support pipe-separated multi-department values (e.g. "Utveckling|Kundvård")
                    String[] departments = department.split("\\|");
                    syncSsoDepartmentGroups(user.getId(), departments);
                } else {
                    log.info("SAML ACS: No department found, skipping group sync");
                }
            }

            // Prevent session fixation: invalidate old session before creating new one
            HttpSession oldSession = req.getSession(false);
            if (oldSession != null) oldSession.invalidate();
            HttpSession session = req.getSession(true);
            session.setAttribute("user", user);
            GroupUtil.refreshSessionGroups(session, user.getId());
            CsrfFilter.generateToken(session);

            resp.sendRedirect(contextPath + "/dashboard.jsp");

        } catch (Exception e) {
            log.error("Failed to process SAML ACS response", e);
            resp.sendRedirect(contextPath + "/login?error=sso");
        }
    }

    /**
     * Find an existing user by email, or create a new SSO user.
     * Updates last_login and full_name for existing users.
     */
    private User findOrCreateSsoUser(String email, String fullName) {
        try (Connection conn = DBUtil.getConnection()) {

            // Try to find existing user by email
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, username, email, full_name, is_admin FROM users WHERE email = ? AND is_active = 1")) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    // Existing user found — update last_login and full_name
                    User user = new User();
                    user.setId(rs.getInt("id"));
                    user.setUsername(rs.getString("username"));
                    user.setEmail(rs.getString("email"));
                    user.setFullName(fullName != null ? fullName : rs.getString("full_name"));
                    user.setAdmin(rs.getBoolean("is_admin"));
                    user.setSsoUser(true);

                    try (PreparedStatement updatePs = conn.prepareStatement(
                            "UPDATE users SET last_login = NOW(), full_name = ? WHERE id = ?")) {
                        updatePs.setString(1, user.getFullName());
                        updatePs.setInt(2, user.getId());
                        updatePs.executeUpdate();
                    }
                    return user;
                }
                } // close try(ResultSet)
            }

            // User not found — create new SSO user
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (username, email, password, full_name, is_active) VALUES (?, ?, ?, ?, 1)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, email); // Use email as username for SSO users
                ps.setString(2, email);
                ps.setString(3, SSO_PASSWORD_PLACEHOLDER);
                ps.setString(4, fullName);
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        User user = new User();
                        user.setId(keys.getInt(1));
                        user.setUsername(email);
                        user.setEmail(email);
                        user.setFullName(fullName);
                        user.setSsoUser(true);
                        return user;
                    }
                }
            }

        } catch (SQLException e) {
            log.error("Failed to find or create SSO user for email={}", email, e);
        }
        return null;
    }

    /**
     * Sync user's SSO department group membership.
     * Supports multiple departments (pipe-separated values from Azure, e.g. "Utveckling|Kundvård").
     *
     * 1. Remove user from all SSO-mapped groups (groups with sso_department IS NOT NULL)
     * 2. For each department: find matching group(s)
     * 3. If no match and auto-create enabled, create a new group
     * 4. Assign user to all matching group(s)
     *
     * This handles department changes: old SSO groups are removed, new ones are assigned.
     * Non-critical: errors are logged but do not prevent login.
     */
    private void syncSsoDepartmentGroups(int userId, String[] departments) {
        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Step 1: Remove user from ALL SSO-mapped groups
                // (groups where sso_department is not null)
                String removeSql = "DELETE FROM user_groups WHERE user_id = ? AND group_id IN " +
                                   "(SELECT id FROM `groups` WHERE sso_department IS NOT NULL)";
                try (PreparedStatement ps = conn.prepareStatement(removeSql)) {
                    ps.setInt(1, userId);
                    int removed = ps.executeUpdate();
                    log.info("SAML ACS: Removed {} old SSO group memberships for userId={}", removed, userId);
                }

                boolean autoCreate = AppConfig.getBoolean("sso.autoCreateGroups", true);

                // Step 2: For each department, find or create matching group and assign
                for (String rawDept : departments) {
                    String dept = rawDept.trim();
                    if (dept.isEmpty()) continue;

                    int matchedGroupId = 0;

                    // 2a: First try exact match on sso_department
                    String findSsoSql = "SELECT id FROM `groups` WHERE sso_department = ?";
                    try (PreparedStatement ps = conn.prepareStatement(findSsoSql)) {
                        ps.setString(1, dept);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                matchedGroupId = rs.getInt("id");
                                assignUserToGroup(conn, userId, matchedGroupId);
                            }
                        }
                    }

                    // 2b: If no sso_department match, try matching by group name
                    //     and link it by setting sso_department on the existing group
                    if (matchedGroupId == 0) {
                        String findNameSql = "SELECT id FROM `groups` WHERE name = ? AND sso_department IS NULL";
                        try (PreparedStatement ps = conn.prepareStatement(findNameSql)) {
                            ps.setString(1, dept);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    matchedGroupId = rs.getInt("id");
                                    // Link existing group to this SSO department
                                    try (PreparedStatement ups = conn.prepareStatement(
                                            "UPDATE `groups` SET sso_department = ? WHERE id = ?")) {
                                        ups.setString(1, dept);
                                        ups.setInt(2, matchedGroupId);
                                        ups.executeUpdate();
                                    }
                                    assignUserToGroup(conn, userId, matchedGroupId);
                                    log.info("SAML ACS: Linked existing group '{}' (id={}) to SSO department", dept, matchedGroupId);
                                }
                            }
                        }
                    }

                    // Step 3: If still no match, optionally auto-create new group
                    if (matchedGroupId == 0 && autoCreate) {
                        String createSql = "INSERT INTO `groups` (name, icon, description, is_hidden, sso_department) " +
                                           "VALUES (?, '\uD83C\uDFE2', ?, 0, ?)";
                        try (PreparedStatement cps = conn.prepareStatement(createSql,
                                PreparedStatement.RETURN_GENERATED_KEYS)) {
                            cps.setString(1, dept);
                            cps.setString(2, "SSO-skapad grupp f\u00F6r avdelning: " + dept);
                            cps.setString(3, dept);
                            cps.executeUpdate();

                            try (ResultSet keys = cps.getGeneratedKeys()) {
                                if (keys.next()) {
                                    matchedGroupId = keys.getInt(1);
                                    assignUserToGroup(conn, userId, matchedGroupId);
                                    log.info("SAML ACS: Auto-created SSO group '{}' (id={})", dept, matchedGroupId);
                                }
                            }
                        }
                    }

                    log.info("SAML ACS: Department '{}' -> groupId={}", dept, matchedGroupId);
                }

                conn.commit();
                log.info("SAML ACS: SSO group sync completed for userId={}, departments={}", userId, String.join("|", departments));

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            // Non-critical: log error but don't prevent login
            log.error("SAML ACS: SSO group sync failed for departments '{}'", String.join("|", departments), e);
        }
    }

    /**
     * Assign a user to a group (idempotent — ignores duplicates).
     */
    private void assignUserToGroup(Connection conn, int userId, int groupId) throws SQLException {
        String sql = "INSERT INTO user_groups (user_id, group_id) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE user_id = user_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, groupId);
            ps.executeUpdate();
        }
    }

    /**
     * Safely extract a single attribute value from the SAML assertion.
     */
    private String getAttributeValue(Auth auth, String attributeName) {
        try {
            Collection<String> values = auth.getAttribute(attributeName);
            if (values != null && !values.isEmpty()) {
                return values.iterator().next();
            }
        } catch (Exception ignored) {
            // Attribute not present
        }
        return null;
    }

}
