package com.infocaption.dashboard.filter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Limits the request body size for non-multipart requests to prevent
 * memory exhaustion / denial-of-service attacks on JSON API endpoints.
 *
 * Multipart requests are excluded because they have their own size limits
 * configured per-servlet in web.xml (e.g., ModuleCreateServlet 50MB).
 *
 * Default max body size: 1 MB (configurable via init-param "maxBytes").
 */
public class BodySizeLimitFilter implements Filter {

    private long maxBytes = 1_048_576; // 1 MB default

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String param = filterConfig.getInitParameter("maxBytes");
        if (param != null && !param.isEmpty()) {
            try {
                maxBytes = Long.parseLong(param);
            } catch (NumberFormatException ignored) {}
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpReq = (HttpServletRequest) request;
            String contentType = httpReq.getContentType();

            // Skip multipart requests (they have their own limits in web.xml)
            boolean isMultipart = contentType != null
                    && contentType.toLowerCase().startsWith("multipart/");

            if (!isMultipart) {
                long contentLength = httpReq.getContentLengthLong();
                if (contentLength > maxBytes) {
                    HttpServletResponse httpResp = (HttpServletResponse) response;
                    httpResp.setStatus(413);
                    httpResp.setContentType("application/json; charset=UTF-8");
                    httpResp.getWriter().write(
                        "{\"error\":\"Request body too large. Max size: " + maxBytes + " bytes\"}");
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {}
}
