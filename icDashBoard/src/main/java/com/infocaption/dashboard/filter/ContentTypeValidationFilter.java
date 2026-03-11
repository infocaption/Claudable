package com.infocaption.dashboard.filter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Validates that POST/PUT/DELETE requests to API endpoints include
 * an appropriate Content-Type header. Prevents accidental misuse and
 * certain CSRF bypass techniques that rely on non-standard content types.
 *
 * Rules:
 * - API endpoints (/api/*) with body must send application/json or multipart/form-data
 * - Non-API endpoints (login, register, forms) accept application/x-www-form-urlencoded
 * - GET/HEAD/OPTIONS requests are always allowed
 * - Requests with no body (Content-Length 0 or absent) are allowed
 */
public class ContentTypeValidationFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpReq = (HttpServletRequest) request;
            String method = httpReq.getMethod().toUpperCase();

            // Only validate body-bearing methods
            if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {

                // Skip validation if no body
                long contentLength = httpReq.getContentLengthLong();
                if (contentLength > 0) {
                    String contentType = httpReq.getContentType();
                    String path = httpReq.getRequestURI();

                    if (path.contains("/api/")) {
                        // API endpoints: require JSON or multipart only (not form-encoded to prevent CSRF bypass)
                        if (contentType == null || (!contentType.toLowerCase().contains("application/json")
                                && !contentType.toLowerCase().contains("multipart/form-data"))) {
                            HttpServletResponse httpResp = (HttpServletResponse) response;
                            httpResp.setStatus(415);
                            httpResp.setContentType("application/json; charset=UTF-8");
                            httpResp.getWriter().write(
                                "{\"error\":\"Unsupported Content-Type. Expected application/json\"}");
                            return;
                        }
                    }
                    // Non-API endpoints: accept form-encoded, JSON, multipart — standard browser behavior
                }
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {}
}
