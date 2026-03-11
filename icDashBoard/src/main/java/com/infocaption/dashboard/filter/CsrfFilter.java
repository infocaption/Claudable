package com.infocaption.dashboard.filter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * CSRF protection filter using Synchronizer Token Pattern.
 *
 * Token is stored in session as "csrfToken" and validated on state-changing
 * requests (POST, PUT, DELETE, PATCH). Token can be submitted via:
 *   - HTTP header: X-CSRF-Token
 *   - Form parameter: _csrf
 *
 * Excluded paths: login, register, logout, SAML endpoints, API key-authenticated imports.
 */
public class CsrfFilter implements Filter {

    private static final String CSRF_TOKEN_ATTR = "csrfToken";
    private static final String CSRF_HEADER = "X-CSRF-Token";
    private static final String CSRF_PARAM = "_csrf";
    private static final SecureRandom random = new SecureRandom();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        // Skip non-state-changing methods
        String method = req.getMethod().toUpperCase();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            ensureToken(req);
            chain.doFilter(request, response);
            return;
        }

        // Skip paths that don't need CSRF protection
        String uri = req.getRequestURI();
        String ctx = req.getContextPath();
        String path = uri.substring(ctx.length());

        if (isExcluded(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Skip CSRF for Bearer token-authenticated API requests
        // (tokens are secret per-user credentials — not vulnerable to CSRF)
        if (path.startsWith("/api/")) {
            String authHeader = req.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                chain.doFilter(request, response);
                return;
            }
        }

        // No session = not logged in, AuthFilter will handle it
        HttpSession session = req.getSession(false);
        if (session == null) {
            chain.doFilter(request, response);
            return;
        }

        // Validate CSRF token
        String sessionToken = (String) session.getAttribute(CSRF_TOKEN_ATTR);
        if (sessionToken == null) {
            // Token not set yet (edge case) - generate and reject
            generateToken(session);
            sendError(req, res);
            return;
        }

        // Check header first, then form parameter, then query string (for multipart forms)
        String submittedToken = req.getHeader(CSRF_HEADER);
        if (submittedToken == null || submittedToken.isEmpty()) {
            submittedToken = req.getParameter(CSRF_PARAM);
        }
        if (submittedToken == null || submittedToken.isEmpty()) {
            String qs = req.getQueryString();
            if (qs != null) {
                for (String param : qs.split("&")) {
                    if (param.startsWith(CSRF_PARAM + "=")) {
                        submittedToken = param.substring(CSRF_PARAM.length() + 1);
                        break;
                    }
                }
            }
        }

        if (submittedToken == null || !MessageDigest.isEqual(
                submittedToken.getBytes(StandardCharsets.UTF_8),
                sessionToken.getBytes(StandardCharsets.UTF_8))) {
            sendError(req, res);
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {}

    private boolean isExcluded(String path) {
        return path.equals("/login") ||
               path.equals("/register") ||
               path.equals("/logout") ||
               path.startsWith("/saml/") ||
               path.startsWith("/shared/") ||
               // API key-authenticated endpoints (scripts, PowerShell)
               path.equals("/api/customer-stats/import") ||
               path.startsWith("/api/drift/") ||
               path.startsWith("/api/cloudguard/");
    }

    private void ensureToken(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute(CSRF_TOKEN_ATTR) == null) {
            generateToken(session);
        }
    }

    /**
     * Generate a cryptographically random CSRF token and store in session.
     */
    public static String generateToken(HttpSession session) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(CSRF_TOKEN_ATTR, token);
        return token;
    }

    private void sendError(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String uri = req.getRequestURI();
        if (uri.contains("/api/")) {
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            res.setContentType("application/json; charset=UTF-8");
            res.getWriter().write("{\"error\":\"CSRF token missing or invalid\"}");
        } else {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "CSRF token missing or invalid");
        }
    }
}
