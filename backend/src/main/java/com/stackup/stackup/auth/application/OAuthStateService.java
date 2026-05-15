package com.stackup.stackup.auth.application;

import com.stackup.stackup.auth.application.dto.OAuthStateIssueResult;
import com.stackup.stackup.auth.domain.OAuthState;
import com.stackup.stackup.auth.domain.OAuthStateRepository;
import com.stackup.stackup.common.config.properties.SecurityProperties;
import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
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
public class OAuthStateService {

    private final OAuthStateRepository oauthStateRepository;
    private final SecurityProperties securityProperties;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public OAuthStateService(
        OAuthStateRepository oauthStateRepository,
        SecurityProperties securityProperties,
        SecureRandom secureRandom,
        Clock clock
    ) {
        this.oauthStateRepository = oauthStateRepository;
        this.securityProperties = securityProperties;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Transactional
    public OAuthStateIssueResult issueGithubStateWithPkce() {
        Instant now = Instant.now(clock);
        oauthStateRepository.deleteByExpiresAtBefore(now);

        String state = randomUrlSafeValue(securityProperties.oauthStateByteLength());
        String codeVerifier = randomUrlSafeValue(securityProperties.pkceCodeVerifierByteLength());
        String codeChallenge = s256Challenge(codeVerifier);

        oauthStateRepository.save(OAuthState.issueGithub(state, codeVerifier, now.plus(securityProperties.oauthStateTtl())));
        return new OAuthStateIssueResult(state, codeChallenge);
    }

    @Transactional
    public String consumeGithubCodeVerifier(String state) {
        if (state == null || state.isBlank()) {
            throw oauthFailed();
        }

        OAuthState oauthState = oauthStateRepository.findByState(state)
            .orElseThrow(this::oauthFailed);

        oauthStateRepository.delete(oauthState);

        if (oauthState.isExpired(Instant.now(clock))) {
            throw oauthFailed();
        }

        return oauthState.getCodeVerifier();
    }

    private String randomUrlSafeValue(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String s256Challenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private DomainException oauthFailed() {
        return new DomainException(ApiErrorCode.AUTH_GITHUB_OAUTH_FAILED);
    }
}
