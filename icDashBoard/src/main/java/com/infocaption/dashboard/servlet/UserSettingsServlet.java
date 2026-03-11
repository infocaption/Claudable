package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.JsonUtil;
import com.infocaption.dashboard.util.PasswordUtil;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User settings API — profile updates and password changes.
 *
 * POST /api/user/settings
 *   action=updateProfile  → update fullName, email
 *   action=changePassword → verify old password, set new password
 */
public class UserSettingsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String SSO_PASSWORD_PLACEHOLDER = "SSO_NO_PASSWORD";

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");

        User user = (User) req.getSession().getAttribute("user");
        if (user == null) {
            resp.setStatus(401);
            resp.getWriter().write("{\"error\":\"Not authenticated\"}");
            return;
        }

        String body = readBody(req);
        String action = JsonUtil.extractJsonString(body, "action");

        if ("updateProfile".equals(action)) {
            handleUpdateProfile(req, resp, user, body);
        } else if ("changePassword".equals(action)) {
            handleChangePassword(resp, user, body);
        } else {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Unknown action\"}");
        }
    }

    private void handleUpdateProfile(HttpServletRequest req, HttpServletResponse resp, User user, String body) throws IOException {
        String fullName = JsonUtil.extractJsonString(body, "fullName");
        String email = JsonUtil.extractJsonString(body, "email");

        if (fullName == null || fullName.trim().isEmpty()) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Namn kr\\u00e4vs\"}");
            return;
        }
        if (email == null || email.trim().isEmpty() || !email.contains("@")) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Ogiltig e-postadress\"}");
            return;
        }

        fullName = fullName.trim();
        email = email.trim();

        try (Connection conn = DBUtil.getConnection()) {
            // Check email uniqueness
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM users WHERE email = ? AND id != ?")) {
                ps.setString(1, email);
                ps.setInt(2, user.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        resp.setStatus(409);
                        resp.getWriter().write("{\"error\":\"E-postadressen anv\\u00e4nds redan av en annan anv\\u00e4ndare\"}");
                        return;
                    }
                }
            }

            // Update profile
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE users SET full_name = ?, email = ? WHERE id = ?")) {
                ps.setString(1, fullName);
                ps.setString(2, email);
                ps.setInt(3, user.getId());
                ps.executeUpdate();
            }

            // Update session user object
            user.setFullName(fullName);
            user.setEmail(email);
            req.getSession().setAttribute("user", user);

            resp.getWriter().write("{\"success\":true,\"fullName\":" + JsonUtil.quote(fullName) +
                    ",\"email\":" + JsonUtil.quote(email) + "}");

        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Databasfel\"}");
        }
    }

    private void handleChangePassword(HttpServletResponse resp, User user, String body) throws IOException {
        String currentPassword = JsonUtil.extractJsonString(body, "currentPassword");
        String newPassword = JsonUtil.extractJsonString(body, "newPassword");
        String confirmPassword = JsonUtil.extractJsonString(body, "confirmPassword");

        if (currentPassword == null || currentPassword.isEmpty() ||
            newPassword == null || newPassword.isEmpty() ||
            confirmPassword == null || confirmPassword.isEmpty()) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Alla l\\u00f6senordsf\\u00e4lt m\\u00e5ste fyllas i\"}");
            return;
        }

        if (newPassword.length() < 6) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Nytt l\\u00f6senord m\\u00e5ste vara minst 6 tecken\"}");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"L\\u00f6senorden matchar inte\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection()) {
            // Fetch current password hash
            String storedHash = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT password FROM users WHERE id = ?")) {
                ps.setInt(1, user.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        storedHash = rs.getString("password");
                    }
                }
            }

            if (storedHash == null) {
                resp.setStatus(404);
                resp.getWriter().write("{\"error\":\"Anv\\u00e4ndare hittades inte\"}");
                return;
            }

            // Block SSO users from changing password
            if (SSO_PASSWORD_PLACEHOLDER.equals(storedHash)) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"SSO-anv\\u00e4ndare kan inte byta l\\u00f6senord\"}");
                return;
            }

            // Verify current password
            if (!PasswordUtil.verify(currentPassword, storedHash)) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Nuvarande l\\u00f6senord \\u00e4r felaktigt\"}");
                return;
            }

            // Update password
            String newHash = PasswordUtil.hash(newPassword);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE users SET password = ? WHERE id = ?")) {
                ps.setString(1, newHash);
                ps.setInt(2, user.getId());
                ps.executeUpdate();
            }

            resp.getWriter().write("{\"success\":true}");

        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Databasfel\"}");
        }
    }

    private String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

}
