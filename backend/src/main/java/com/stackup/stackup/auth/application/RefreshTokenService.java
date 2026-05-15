package com.stackup.stackup.auth.application;

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
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public RefreshTokenService(
        RefreshTokenRepository refreshTokenRepository,
        UserRepository userRepository,
        SecurityProperties securityProperties,
        SecureRandom secureRandom,
        Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.securityProperties = securityProperties;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Transactional
    public RefreshTokenIssueResult issue(Long userId) {
        String rawToken = generateRawToken();
        String tokenHash = hash(rawToken);
        User user = userRepository.getReferenceById(userId);
        Instant expiresAt = Instant.now(clock).plusSeconds(securityProperties.refreshTokenTtlSeconds());

        refreshTokenRepository.save(RefreshToken.issue(user, tokenHash, null, expiresAt));
        return new RefreshTokenIssueResult(rawToken, securityProperties.refreshTokenTtlSeconds());
    }

    @Transactional
    public RefreshTokenRotationResult rotate(String rawRefreshToken) {
        RefreshToken refreshToken = getUsableRefreshToken(rawRefreshToken);
        Long userId = refreshToken.getUser().getId();
        refreshToken.revoke();
        return new RefreshTokenRotationResult(userId, issue(userId));
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
            .ifPresent(RefreshToken::revoke);
    }

    private RefreshToken getUsableRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new DomainException(ApiErrorCode.AUTH_INVALID_TOKEN);
        }

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
            .orElseThrow(() -> new DomainException(ApiErrorCode.AUTH_INVALID_TOKEN));

        if (refreshToken.isRevoked()) {
            throw new DomainException(ApiErrorCode.AUTH_REVOKED_TOKEN);
        }
        if (refreshToken.isExpired(Instant.now(clock))) {
            refreshToken.revoke();
            throw new DomainException(ApiErrorCode.AUTH_EXPIRED_TOKEN);
        }

        return refreshToken;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[securityProperties.refreshTokenByteLength()];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
