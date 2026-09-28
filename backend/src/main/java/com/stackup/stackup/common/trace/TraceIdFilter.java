package com.stackup.stackup.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 모든 요청에 traceId 를 붙인다.
 *
 * <p><b>순서가 중요하다.</b> {@code @Order} 가 없으면 Spring Boot 는 이 필터를
 * LOWEST_PRECEDENCE 로 등록하는데, Spring Security 체인은 -100 이라 <b>보안 체인이 먼저
 * 돈다.</b> 그러면 인증 실패처럼 보안 체인 안에서 끝나는 요청은 이 필터에 닿지 못하고
 * traceId 가 null 이 된다 — 운영 로그에 실제로 그렇게 찍혀 있었다:
 *
 * <pre>Authentication failed. code=AUTH_INVALID_TOKEN, traceId=null, uri=/api/.env</pre>
 *
 * 추적 ID 가 가장 필요한 순간(인증 실패·공격 스캔)에 정확히 빠져 있었다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
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
