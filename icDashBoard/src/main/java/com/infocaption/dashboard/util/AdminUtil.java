package com.infocaption.dashboard.util;

import com.infocaption.dashboard.model.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Utility for admin role checks.
 * Provides guard methods that send appropriate HTTP responses when access is denied.
 */
public class AdminUtil {

    /**
     * Check if the current session user is an admin.
     * Returns the User if admin, null otherwise (and sends 401/403 response).
     */
    public static User requireAdmin(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write("{\"error\":\"Not authenticated\"}");
            return null;
        }
        User user = (User) session.getAttribute("user");
        if (!user.isAdmin()) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write("{\"error\":\"Admin access required\"}");
            return null;
        }
        return user;
    }

    /**
     * Check if a user object is admin (non-HTTP context).
     */
    public static boolean isAdmin(User user) {
        return user != null && user.isAdmin();
    }
}
