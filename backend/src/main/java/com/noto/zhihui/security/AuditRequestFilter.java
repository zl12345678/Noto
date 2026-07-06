package com.noto.zhihui.security;

import com.noto.zhihui.common.util.RequestClientUtils;
import com.noto.zhihui.service.AuditLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

public class AuditRequestFilter extends OncePerRequestFilter {

    private final AuditLogService auditLogService;

    public AuditRequestFilter(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long started = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            logRequest(request, response, System.currentTimeMillis() - started);
        }
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        Long userId = UserContext.getUserId();
        if (userId == null || shouldSkip(request)) {
            return;
        }
        String path = request.getRequestURI();
        String query = request.getQueryString();
        auditLogService.logOperation(
                userId,
                null,
                "api." + request.getMethod(),
                "http_request",
                null,
                RequestClientUtils.clientIp(request),
                RequestClientUtils.userAgent(request),
                Map.of(
                        "method", request.getMethod(),
                        "path", path,
                        "query", StringUtils.hasText(query) ? query : "",
                        "status", response.getStatus(),
                        "durationMs", durationMs,
                        "referer", StringUtils.hasText(request.getHeader(HttpHeaders.REFERER))
                                ? request.getHeader(HttpHeaders.REFERER)
                                : ""
                )
        );
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/")) {
            return true;
        }
        return path.startsWith("/api/v1/health")
                || path.startsWith("/api/v1/admin/audit-logs")
                || path.startsWith("/api/v1/attachments/")
                || path.startsWith("/api/v1/share/public/");
    }
}
