package com.stackup.stackup.common.security;

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

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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
            } catch (RuntimeException exception) {
                SecurityContextHolder.clearContext();
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
