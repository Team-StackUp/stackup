package com.stackup.stackup.common.security;

import com.stackup.stackup.common.exception.ApiErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// /api/stream/** 경로에 한해 ?token=<STREAM_JWT> query parameter 로 인증을 처리한다.
// 브라우저 EventSource 가 Authorization 헤더를 못 보내므로 stream-token 흐름을 받치는 전용 필터.
// 검증된 토큰은 STREAM scope 만 인정 — 일반 access token 으로는 동작하지 않음.
@Component
public class StreamTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(StreamTokenAuthenticationFilter.class);
    private static final String STREAM_PATH_PREFIX = "/api/stream/";
    private static final String TOKEN_PARAM = "token";

    private final StreamTokenProvider streamTokenProvider;

    public StreamTokenAuthenticationFilter(StreamTokenProvider streamTokenProvider) {
        this.streamTokenProvider = streamTokenProvider;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(STREAM_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        String token = request.getParameter(TOKEN_PARAM);
        if (token == null || token.isBlank()) {
            // query token 없으면 일반 JWT 필터가 Authorization 헤더로 처리하도록 통과
            chain.doFilter(request, response);
            return;
        }

        try {
            Long userId = streamTokenProvider.getUserId(token);
            UserPrincipal principal = new UserPrincipal(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, token, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException exception) {
            request.setAttribute(
                JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE,
                ApiErrorCode.AUTH_INVALID_TOKEN
            );
            SecurityContextHolder.clearContext();
            log.warn("stream-token authentication failed. uri={}", request.getRequestURI());
        }
        chain.doFilter(request, response);
    }
}
