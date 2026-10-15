package com.vidhura.ai.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${rate-limit.requests-per-minute:60}")
    private int requestsPerMinute;

    private final Map<String, RateBucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Skip rate limiting for health checks
        String path = request.getRequestURI();
        if (path.startsWith("/api/health") || path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        RateBucket bucket = buckets.computeIfAbsent(clientIp, k -> new RateBucket());

        if (bucket.tryConsume(requestsPerMinute)) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getRemaining(requestsPerMinute)));
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"error\":\"Rate limit exceeded. Please try again later.\"}");
        }

        // Periodic cleanup of stale buckets (every 5 minutes)
        if (System.currentTimeMillis() % 300000 < 1000) {
            long cutoff = System.currentTimeMillis() - 300000;
            buckets.entrySet().removeIf(e -> e.getValue().lastAccess.get() < cutoff);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private static class RateBucket {
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        final AtomicLong lastAccess = new AtomicLong(System.currentTimeMillis());

        boolean tryConsume(int limit) {
            lastAccess.set(System.currentTimeMillis());
            long now = System.currentTimeMillis();
            long start = windowStart.get();

            // Reset window every minute
            if (now - start >= 60000) {
                count.set(0);
                windowStart.set(now);
            }

            return count.incrementAndGet() <= limit;
        }

        int getRemaining(int limit) {
            return Math.max(0, limit - count.get());
        }
    }
}
