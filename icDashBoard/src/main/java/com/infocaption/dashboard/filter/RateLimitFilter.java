package com.infocaption.dashboard.filter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate-limiting filter for login and registration endpoints.
 * Uses a sliding window approach: tracks request counts per IP per time window.
 *
 * Default: max 10 requests per 60 seconds per IP.
 * Configurable via init-params "maxRequests" and "windowSeconds".
 *
 * Note: This is a per-JVM implementation. For clustered deployments,
 * consider an external rate limiter (e.g., Redis-backed).
 */
public class RateLimitFilter implements Filter {

    private int maxRequests = 10;
    private long windowMillis = 60_000; // 60 seconds
    private String trustedProxyIp = null; // Set via init-param "trustedProxy" to trust X-Forwarded-For

    /** IP -> WindowEntry (count + window start time) */
    private final ConcurrentHashMap<String, WindowEntry> clients = new ConcurrentHashMap<>();

    /** Cleanup counter — every N requests, purge stale entries */
    private final AtomicInteger requestCounter = new AtomicInteger(0);
    private static final int CLEANUP_INTERVAL = 100;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String maxParam = filterConfig.getInitParameter("maxRequests");
        if (maxParam != null && !maxParam.isEmpty()) {
            try { maxRequests = Integer.parseInt(maxParam); } catch (NumberFormatException ignored) {}
        }
        String windowParam = filterConfig.getInitParameter("windowSeconds");
        if (windowParam != null && !windowParam.isEmpty()) {
            try { windowMillis = Long.parseLong(windowParam) * 1000L; } catch (NumberFormatException ignored) {}
        }
        trustedProxyIp = filterConfig.getInitParameter("trustedProxy");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpReq = (HttpServletRequest) request;

            // Only rate-limit POST requests (login/register submissions)
            if ("POST".equalsIgnoreCase(httpReq.getMethod())) {
                String clientIp = getClientIp(httpReq);
                long now = System.currentTimeMillis();

                // Periodic cleanup of stale entries
                if (requestCounter.incrementAndGet() % CLEANUP_INTERVAL == 0) {
                    cleanupStaleEntries(now);
                }

                WindowEntry entry = clients.compute(clientIp, (ip, existing) -> {
                    if (existing == null || (now - existing.windowStart) > windowMillis) {
                        // New window
                        return new WindowEntry(now, 1);
                    } else {
                        existing.count++;
                        return existing;
                    }
                });

                if (entry.count > maxRequests) {
                    HttpServletResponse httpResp = (HttpServletResponse) response;
                    httpResp.setStatus(429);
                    httpResp.setContentType("text/html; charset=UTF-8");
                    httpResp.setHeader("Retry-After", String.valueOf(windowMillis / 1000));
                    httpResp.getWriter().write(
                        "<html><body><h2>429 — F\u00f6r m\u00e5nga f\u00f6rs\u00f6k</h2>" +
                        "<p>V\u00e4nta en minut och f\u00f6rs\u00f6k igen.</p></body></html>");
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Get client IP, trusting X-Forwarded-For only when request comes from the trusted proxy.
     * Without trustedProxy configured, always uses getRemoteAddr() (safe default).
     */
    private String getClientIp(HttpServletRequest request) {
        if (trustedProxyIp != null && trustedProxyIp.equals(request.getRemoteAddr())) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                return xff.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void cleanupStaleEntries(long now) {
        clients.entrySet().removeIf(e -> (now - e.getValue().windowStart) > windowMillis * 2);
    }

    @Override
    public void destroy() {
        clients.clear();
    }

    private static class WindowEntry {
        long windowStart;
        int count;

        WindowEntry(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
