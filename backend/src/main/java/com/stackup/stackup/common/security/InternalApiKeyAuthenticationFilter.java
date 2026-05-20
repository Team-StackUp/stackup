package com.stackup.stackup.common.security;

import com.stackup.stackup.common.config.properties.SecurityProperties;
import com.stackup.stackup.common.exception.ApiErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /api/internal/* 경로는 X-Internal-API-Key 헤더로 인증한다.
 * 유효 시 INTERNAL 권한을 가진 Authentication 을 SecurityContext 에 주입.
 * 그 외 경로에서는 아무것도 하지 않고 통과 (JWT 필터가 이어서 처리).
 */
@Component
public class InternalApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-API-Key";
    public static final String PRINCIPAL = "internal";
    public static final String AUTHORITY = "INTERNAL";

    private final SecurityProperties properties;

    public InternalApiKeyAuthenticationFilter(SecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/internal/")) {
            chain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(HEADER);
        String expected = properties.internalApiKey();
        if (expected == null || expected.isBlank() || provided == null || !constantTimeEquals(provided, expected)) {
            // 인증 실패 — 권한 부여 X. SecurityConfig 에서 hasAuthority("INTERNAL") 로 차단 후 401.
            request.setAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE, ApiErrorCode.INTERNAL_AUTH_FAILED);
            chain.doFilter(request, response);
            return;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            PRINCIPAL,
            null,
            List.of(new SimpleGrantedAuthority(AUTHORITY))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes();
        byte[] y = b.getBytes();
        if (x.length != y.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < x.length; i++) {
            diff |= x[i] ^ y[i];
        }
        return diff == 0;
    }
}
