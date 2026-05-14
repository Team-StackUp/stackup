package com.stackup.stackup.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.auth.application.dto.RefreshTokenIssueResult;
import com.stackup.stackup.auth.application.dto.RefreshTokenRotationResult;
import com.stackup.stackup.auth.domain.RefreshToken;
import com.stackup.stackup.auth.domain.RefreshTokenRepository;
import com.stackup.stackup.common.config.properties.SecurityProperties;
import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-14T00:00:00Z");

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void issue_savesOnlyTokenHash() {
        User user = user(1L);
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        RefreshTokenService service = service();

        RefreshTokenIssueResult result = service.issue(1L);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());

        RefreshToken savedToken = tokenCaptor.getValue();
        assertThat(result.rawToken()).isNotBlank();
        assertThat(result.maxAgeSeconds()).isEqualTo(1209600);
        assertThat(savedToken.getTokenHash()).isEqualTo(hash(result.rawToken()));
        assertThat(savedToken.getTokenHash()).isNotEqualTo(result.rawToken());
        assertThat(savedToken.getExpiresAt()).isEqualTo(NOW.plusSeconds(1209600));
    }

    @Test
    void rotate_revokesOldTokenAndIssuesNewToken() {
        User user = user(1L);
        RefreshToken oldToken = RefreshToken.issue(user, hash("old-refresh-token"), null, NOW.plusSeconds(60));
        when(refreshTokenRepository.findByTokenHash(hash("old-refresh-token"))).thenReturn(Optional.of(oldToken));
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        RefreshTokenService service = service();

        RefreshTokenRotationResult result = service.rotate("old-refresh-token");

        assertThat(oldToken.isRevoked()).isTrue();
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.refreshToken().rawToken()).isNotBlank();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void rotate_rejectsRevokedToken() {
        User user = user(1L);
        RefreshToken oldToken = RefreshToken.issue(user, hash("old-refresh-token"), null, NOW.plusSeconds(60));
        oldToken.revoke();
        when(refreshTokenRepository.findByTokenHash(hash("old-refresh-token"))).thenReturn(Optional.of(oldToken));
        RefreshTokenService service = service();

        assertThatThrownBy(() -> service.rotate("old-refresh-token"))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_REVOKED_TOKEN)
            );
    }

    @Test
    void rotate_revokesAndRejectsExpiredToken() {
        User user = user(1L);
        RefreshToken oldToken = RefreshToken.issue(user, hash("old-refresh-token"), null, NOW.minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(hash("old-refresh-token"))).thenReturn(Optional.of(oldToken));
        RefreshTokenService service = service();

        assertThatThrownBy(() -> service.rotate("old-refresh-token"))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_EXPIRED_TOKEN)
            );
        assertThat(oldToken.isRevoked()).isTrue();
    }

    private RefreshTokenService service() {
        return new RefreshTokenService(
            refreshTokenRepository,
            userRepository,
            new SecurityProperties("jwt-secret", "encryption-key", 900, 1209600),
            new SecureRandom(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static User user(Long id) {
        User user = User.createGithubUser(123L, "octocat", null, null, "encrypted-token");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
