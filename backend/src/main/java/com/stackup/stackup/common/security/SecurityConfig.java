package com.stackup.stackup.common.security;

import com.stackup.stackup.common.config.properties.CorsProperties;
import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.trace.TraceContext;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsProperties corsProperties;

    public SecurityConfig(
        JwtAuthenticationFilter jwtAuthenticationFilter,
        CorsProperties corsProperties
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint()))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/api/auth/github",
                    "/api/auth/github/callback",
                    "/api/auth/refresh",
                    "/api/auth/logout",
                    "/api/system/live",
                    "/api/system/ready",
                    "/api/system/health",
                    "/api/v3/api-docs/**",
                    "/api/swagger-ui/**",
                    "/api/swagger-ui.html",
                    "/actuator/**"
                ).permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
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
            ApiErrorCode errorCode = ApiErrorCode.AUTH_INVALID_TOKEN;
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                {"code":"%s","message":"%s","traceId":"%s","timestamp":"%s","details":{}}
                """.formatted(
                escape(errorCode.name()),
                escape(errorCode.getDefaultMessage()),
                escape(TraceContext.getTraceId()),
                escape(java.time.Instant.now().toString())
            ));
        };
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
