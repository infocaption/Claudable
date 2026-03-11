package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.PasswordUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class RegisterServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(RegisterServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.getRequestDispatcher("/register.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String username = req.getParameter("username");
        String email = req.getParameter("email");
        String fullName = req.getParameter("fullName");
        String password = req.getParameter("password");
        String confirmPassword = req.getParameter("confirmPassword");

        // Validation
        if (username == null || username.trim().isEmpty() ||
            email == null || email.trim().isEmpty() ||
            fullName == null || fullName.trim().isEmpty() ||
            password == null || password.isEmpty()) {
            req.setAttribute("error", "Alla f\u00e4lt m\u00e5ste fyllas i.");
            preserveFormData(req, username, email, fullName);
            req.getRequestDispatcher("/register.jsp").forward(req, resp);
            return;
        }

        username = username.trim();
        email = email.trim();
        fullName = fullName.trim();

        if (username.length() < 3 || username.length() > 50) {
            req.setAttribute("error", "Anv\u00e4ndarnamn m\u00e5ste vara 3\u201350 tecken.");
            preserveFormData(req, username, email, fullName);
            req.getRequestDispatcher("/register.jsp").forward(req, resp);
            return;
        }

        if (password.length() < 6) {
            req.setAttribute("error", "L\u00f6senord m\u00e5ste vara minst 6 tecken.");
            preserveFormData(req, username, email, fullName);
            req.getRequestDispatcher("/register.jsp").forward(req, resp);
            return;
        }

        if (!password.equals(confirmPassword)) {
            req.setAttribute("error", "L\u00f6senorden matchar inte.");
            preserveFormData(req, username, email, fullName);
            req.getRequestDispatcher("/register.jsp").forward(req, resp);
            return;
        }

        String hashedPassword = PasswordUtil.hash(password);

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO users (username, email, full_name, password) VALUES (?, ?, ?, ?)")) {

            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, fullName);
            ps.setString(4, hashedPassword);
            ps.executeUpdate();

            resp.sendRedirect(req.getContextPath() + "/login?registered=true");
            return;

        } catch (SQLException e) {
            if (e.getMessage().contains("Duplicate entry")) {
                if (e.getMessage().contains("username")) {
                    req.setAttribute("error", "Anv\u00e4ndarnamnet \u00e4r redan taget.");
                } else if (e.getMessage().contains("email")) {
                    req.setAttribute("error", "E-postadressen \u00e4r redan registrerad.");
                } else {
                    req.setAttribute("error", "Anv\u00e4ndarnamnet eller e-postadressen \u00e4r redan registrerad.");
                }
            } else {
                log.error("Failed to register user '{}'", username, e);
                req.setAttribute("error", "Ett tekniskt fel intr\u00e4ffade. F\u00f6rs\u00f6k igen.");
            }
            preserveFormData(req, username, email, fullName);
            req.getRequestDispatcher("/register.jsp").forward(req, resp);
        }
    }

    private void preserveFormData(HttpServletRequest req, String username, String email, String fullName) {
        req.setAttribute("username", username);
        req.setAttribute("email", email);
        req.setAttribute("fullName", fullName);
    }
}
