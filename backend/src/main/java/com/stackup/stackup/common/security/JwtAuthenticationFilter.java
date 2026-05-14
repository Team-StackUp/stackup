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

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_CODE_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".AUTH_ERROR_CODE";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveBearerToken(request);

        if (token != null) {
            try {
                Long userId = jwtTokenProvider.getUserId(token);
                List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
                UserPrincipal principal = new UserPrincipal(userId, null, authorities);
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, token, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtAuthenticationException exception) {
                request.setAttribute(AUTH_ERROR_CODE_ATTRIBUTE, exception.getErrorCode());
                SecurityContextHolder.clearContext();
                log.warn("JWT authentication failed. code={}, uri={}", exception.getErrorCode().name(), request.getRequestURI());
            } catch (RuntimeException exception) {
                request.setAttribute(AUTH_ERROR_CODE_ATTRIBUTE, ApiErrorCode.AUTH_INVALID_TOKEN);
                SecurityContextHolder.clearContext();
                log.warn("JWT authentication failed. code={}, uri={}", ApiErrorCode.AUTH_INVALID_TOKEN.name(), request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isBlank() ? null : token;
    }
}
