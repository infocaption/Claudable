package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.filter.CsrfFilter;
import com.infocaption.dashboard.util.AuditUtil;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.GroupUtil;
import com.infocaption.dashboard.util.PasswordUtil;

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

public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(LoginServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            resp.sendRedirect(req.getContextPath() + "/dashboard.jsp");
            return;
        }
        req.getRequestDispatcher("/login.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String username = req.getParameter("username");
        String password = req.getParameter("password");

        if (username == null || username.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            req.setAttribute("error", "Fyll i b\u00e5de anv\u00e4ndarnamn och l\u00f6senord.");
            req.getRequestDispatcher("/login.jsp").forward(req, resp);
            return;
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, username, email, password, full_name, profile_picture_url, is_admin FROM users WHERE username = ? AND is_active = 1")) {

            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {

            if (rs.next() && PasswordUtil.verify(password, rs.getString("password"))) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setUsername(rs.getString("username"));
                user.setEmail(rs.getString("email"));
                user.setFullName(rs.getString("full_name"));
                user.setProfilePictureUrl(rs.getString("profile_picture_url"));
                user.setAdmin(rs.getBoolean("is_admin"));
                user.setSsoUser(false);

                // Prevent session fixation: invalidate old session before creating new one
                HttpSession oldSession = req.getSession(false);
                if (oldSession != null) oldSession.invalidate();
                HttpSession session = req.getSession(true);
                session.setAttribute("user", user);
                GroupUtil.refreshSessionGroups(session, user.getId());
                CsrfFilter.generateToken(session);

                // Update last_login
                try (PreparedStatement updatePs = conn.prepareStatement(
                        "UPDATE users SET last_login = NOW() WHERE id = ?")) {
                    updatePs.setInt(1, user.getId());
                    updatePs.executeUpdate();
                }

                AuditUtil.logEvent(AuditUtil.LOGIN_SUCCESS, user.getId(), req, "auth", null, "Login via password");

                resp.sendRedirect(req.getContextPath() + "/dashboard.jsp");
                return;
            }
            } // close try(ResultSet)
        } catch (SQLException e) {
            log.error("Failed to authenticate user '{}'", username, e);
            req.setAttribute("error", "Ett tekniskt fel intr\u00e4ffade. F\u00f6rs\u00f6k igen.");
            req.getRequestDispatcher("/login.jsp").forward(req, resp);
            return;
        }

        AuditUtil.logFailedLogin(username, req);
        req.setAttribute("error", "Felaktigt anv\u00e4ndarnamn eller l\u00f6senord.");
        req.setAttribute("username", username);
        req.getRequestDispatcher("/login.jsp").forward(req, resp);
    }
}
