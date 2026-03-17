package com.infocaption.dashboard.filter;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.servlet.ApiTokenServlet;
import com.infocaption.dashboard.util.AppConfig;
import com.infocaption.dashboard.util.SecretsConfig;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class AuthFilter implements Filter {

    private static final String TOKEN_USER_ATTR = "apiTokenUser";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String uri = req.getRequestURI();
        String contextPath = req.getContextPath();

        // Allow webhook inbound endpoint (uses own auth)
        if (uri.startsWith(contextPath + "/api/webhook/inbound/")) {
            chain.doFilter(request, response);
            return;
        }

        // Allow API key authentication for machine-to-machine endpoints
        if (uri.startsWith(contextPath + "/api/customer-stats/import") ||
            uri.startsWith(contextPath + "/api/drift/") ||
            uri.startsWith(contextPath + "/api/cloudguard/report") ||
            uri.startsWith(contextPath + "/api/cloudguard/resolve")) {
            String apiKey = req.getHeader("X-API-Key");
            String expectedKey = AppConfig.get("api.importKey", SecretsConfig.get("api.importKey", ""));
            if (apiKey != null && MessageDigest.isEqual(
                    apiKey.getBytes(StandardCharsets.UTF_8),
                    expectedKey.getBytes(StandardCharsets.UTF_8))) {
                chain.doFilter(request, response);
                return;
            }
        }

        // Bearer token authentication for API endpoints (new token system)
        if (uri.startsWith(contextPath + "/api/")) {
            String authHeader = req.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String rawToken = authHeader.substring(7).trim();
                User tokenUser = ApiTokenServlet.validateToken(rawToken);
                if (tokenUser != null) {
                    // Store validated user on request for downstream servlets
                    req.setAttribute(TOKEN_USER_ATTR, tokenUser);
                    chain.doFilter(request, response);
                    return;
                }
                // Invalid token — fall through to session check or 401
            }
        }

        HttpSession session = req.getSession(false);
        boolean loggedIn = (session != null && session.getAttribute("user") != null);

        if (loggedIn) {
            chain.doFilter(request, response);
        } else {
            // For API calls, return 401 JSON instead of redirect
            if (uri.startsWith(contextPath + "/api/")) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("application/json; charset=UTF-8");
                res.getWriter().write("{\"error\":\"Not authenticated\"}");
            } else {
                res.sendRedirect(contextPath + "/login");
            }
        }
    }

    @Override
    public void destroy() {}
}
