package com.stackup.stackup.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stackup.stackup.common.config.properties.SecurityProperties;
import com.stackup.stackup.common.config.properties.SseProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class StreamTokenProviderTest {

    private StreamTokenProvider provider() {
        SecurityProperties sec = Mockito.mock(SecurityProperties.class);
        Mockito.when(sec.jwtSecret()).thenReturn("test-jwt-secret-test-jwt-secret-32b");
        SseProperties sse = Mockito.mock(SseProperties.class);
        Mockito.when(sse.streamTokenTtl()).thenReturn(Duration.ofSeconds(60));
        return new StreamTokenProvider(sec, sse);
    }

    @Test
    void sessionScopedToken_carriesResourceClaims() {
        StreamTokenProvider p = provider();
        String token = p.createStreamToken(7L, "SESSION", 99L);
        StreamTokenProvider.StreamTokenClaims claims = p.parseClaims(token);
        assertThat(claims.userId()).isEqualTo(7L);
        assertThat(claims.resourceType()).isEqualTo("SESSION");
        assertThat(claims.resourceId()).isEqualTo(99L);
    }

    @Test
    void userScopedToken_carriesUserResource() {
        StreamTokenProvider p = provider();
        String token = p.createStreamToken(7L, "USER", 7L);
        StreamTokenProvider.StreamTokenClaims claims = p.parseClaims(token);
        assertThat(claims.resourceType()).isEqualTo("USER");
        assertThat(claims.resourceId()).isEqualTo(7L);
    }

    @Test
    void tamperedToken_rejected() {
        StreamTokenProvider p = provider();
        assertThatThrownBy(() -> p.parseClaims("not.a.token")).isInstanceOf(RuntimeException.class);
    }
}
