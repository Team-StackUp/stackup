package com.stackup.stackup.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.auth.application.dto.OAuthStateIssueResult;
import com.stackup.stackup.auth.domain.OAuthState;
import com.stackup.stackup.auth.domain.OAuthStateRepository;
import com.stackup.stackup.common.config.properties.SecurityProperties;
import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-14T00:00:00Z");

    @Mock
    private OAuthStateRepository oauthStateRepository;

    @Test
    void issueGithubStateWithPkce_savesStateAndReturnsChallenge() throws Exception {
        OAuthStateService service = new OAuthStateService(
            oauthStateRepository,
            securityProperties(),
            new SecureRandom(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        OAuthStateIssueResult result = service.issueGithubStateWithPkce();

        ArgumentCaptor<OAuthState> stateCaptor = ArgumentCaptor.forClass(OAuthState.class);
        verify(oauthStateRepository).deleteByExpiresAtBefore(NOW);
        verify(oauthStateRepository).save(stateCaptor.capture());

        OAuthState savedState = stateCaptor.getValue();
        assertThat(result.state()).isEqualTo(savedState.getState());
        assertThat(result.state()).isNotBlank();
        assertThat(savedState.getCodeVerifier()).isNotBlank();
        assertThat(result.codeChallenge()).isEqualTo(s256(savedState.getCodeVerifier()));
        assertThat(savedState.getExpiresAt()).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    void consumeGithubCodeVerifier_deletesStateAndReturnsVerifier() {
        OAuthState oauthState = OAuthState.issueGithub("state", "code-verifier", NOW.plusSeconds(60));
        when(oauthStateRepository.findByState("state")).thenReturn(Optional.of(oauthState));

        OAuthStateService service = new OAuthStateService(
            oauthStateRepository,
            securityProperties(),
            new SecureRandom(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        String codeVerifier = service.consumeGithubCodeVerifier("state");

        assertThat(codeVerifier).isEqualTo("code-verifier");
        verify(oauthStateRepository).delete(oauthState);
    }

    @Test
    void consumeGithubCodeVerifier_rejectsExpiredState() {
        OAuthState oauthState = OAuthState.issueGithub("state", "code-verifier", NOW.minusSeconds(1));
        when(oauthStateRepository.findByState("state")).thenReturn(Optional.of(oauthState));

        OAuthStateService service = new OAuthStateService(
            oauthStateRepository,
            securityProperties(),
            new SecureRandom(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.consumeGithubCodeVerifier("state"))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_GITHUB_OAUTH_FAILED)
            );
        verify(oauthStateRepository).delete(oauthState);
    }

    @Test
    void consumeGithubCodeVerifier_rejectsMissingState() {
        OAuthStateService service = new OAuthStateService(
            oauthStateRepository,
            securityProperties(),
            new SecureRandom(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.consumeGithubCodeVerifier(" "))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_GITHUB_OAUTH_FAILED)
            );
    }

    private static String s256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static SecurityProperties securityProperties() {
        return new SecurityProperties(
            "jwt-secret",
            "encryption-key",
            900,
            1209600,
            "Bearer",
            Duration.ofMinutes(5),
            32,
            32,
            32,
            "refresh_token",
            "/api/auth",
            "Strict",
            true,
            true
        );
    }
}
