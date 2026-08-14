package com.stackup.stackup.common.security;

import com.stackup.stackup.common.config.properties.CorsProperties;
import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.trace.TraceContext;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InternalApiKeyAuthenticationFilter internalApiKeyAuthenticationFilter;
    private final CorsProperties corsProperties;

    public SecurityConfig(
        JwtAuthenticationFilter jwtAuthenticationFilter,
        InternalApiKeyAuthenticationFilter internalApiKeyAuthenticationFilter,
        CorsProperties corsProperties
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.internalApiKeyAuthenticationFilter = internalApiKeyAuthenticationFilter;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler())
            )
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/api/auth/github",
                    "/api/auth/github/callback",
                    "/api/auth/google",
                    "/api/auth/google/callback",
                    "/api/auth/refresh",
                    "/api/auth/logout",
                    "/api/system/live",
                    "/api/system/ready",
                    "/api/system/health",
                    "/api/v3/api-docs/**",
                    "/api/swagger-ui/**",
                    "/api/swagger-ui.html",
                    "/actuator/**",
                    "/api/public/**"
                ).permitAll()
                .requestMatchers("/api/internal/**").hasAuthority(InternalApiKeyAuthenticationFilter.AUTHORITY)
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(internalApiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of(TraceContext.TRACE_ID_HEADER, HttpHeaders.AUTHORIZATION));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            ApiErrorCode errorCode = resolveAuthenticationErrorCode(request.getAttribute(
                JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE
            ));
            log.warn("Authentication failed. code={}, traceId={}, uri={}",
                errorCode.name(), TraceContext.getTraceId(), request.getRequestURI());
            writeErrorResponse(response, errorCode);
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            ApiErrorCode errorCode = ApiErrorCode.ACCESS_DENIED;
            log.warn("Access denied. code={}, traceId={}, uri={}",
                errorCode.name(), TraceContext.getTraceId(), request.getRequestURI());
            writeErrorResponse(response, errorCode);
        };
    }

    private ApiErrorCode resolveAuthenticationErrorCode(Object errorCodeAttribute) {
        if (errorCodeAttribute instanceof ApiErrorCode errorCode) {
            return errorCode;
        }
        return ApiErrorCode.AUTH_INVALID_TOKEN;
    }

    private void writeErrorResponse(HttpServletResponse response, ApiErrorCode errorCode) throws java.io.IOException {
        response.setStatus(errorCode.getStatus().value());
        // charset 을 지정하지 않으면 getWriter() 가 ISO-8859-1 로 인코딩해 한글이 "???" 로 나간다.
        // 이 경로는 만료 토큰 401 처럼 사용자가 가장 자주 만나는 응답이라 그대로 화면에 노출된다.
        // (도메인 예외는 Spring 메시지 컨버터를 타서 영향 없음 — 여기만 직접 쓴다)
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
            {"code":"%s","message":"%s","traceId":"%s","timestamp":"%s","details":{}}
            """.formatted(
            escape(errorCode.name()),
            escape(errorCode.getDefaultMessage()),
            escape(TraceContext.getTraceId()),
            escape(Instant.now().toString())
        ));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
