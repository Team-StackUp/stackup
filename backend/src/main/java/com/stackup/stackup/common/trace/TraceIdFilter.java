package com.stackup.stackup.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = resolveTraceId(request);

        try {
            TraceContext.setTraceId(traceId);
            response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TraceContext.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return traceId;
    }
}
