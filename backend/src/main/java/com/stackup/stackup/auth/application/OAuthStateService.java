package com.stackup.stackup.auth.application;

import com.stackup.stackup.auth.application.dto.OAuthStateIssueResult;
import com.stackup.stackup.user.domain.OAuthProvider;
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
    public OAuthStateIssueResult issueStateWithPkce(OAuthProvider provider) {
        Instant now = Instant.now(clock);
        oauthStateRepository.deleteByExpiresAtBefore(now);

        String state = randomUrlSafeValue(securityProperties.oauthStateByteLength());
        String codeVerifier = randomUrlSafeValue(securityProperties.pkceCodeVerifierByteLength());
        String codeChallenge = s256Challenge(codeVerifier);

        oauthStateRepository.save(
            OAuthState.issue(provider, state, codeVerifier, now.plus(securityProperties.oauthStateTtl()))
        );
        return new OAuthStateIssueResult(state, codeChallenge);
    }

    /**
     * state 를 소비하고 code_verifier 를 돌려준다.
     *
     * provider 를 함께 검증하는 이유 — state 는 콜백 엔드포인트별로 나뉘지 않는 하나의 테이블에
     * 들어간다. 검증하지 않으면 GitHub 용으로 발급한 state 를 Google 콜백에 넘겨 흐름을 섞을 수
     * 있다(그 반대도 마찬가지).
     */
    @Transactional
    public String consumeCodeVerifier(OAuthProvider provider, String state) {
        if (state == null || state.isBlank()) {
            throw oauthFailed(provider);
        }

        OAuthState oauthState = oauthStateRepository.findByState(state)
            .orElseThrow(() -> oauthFailed(provider));

        oauthStateRepository.delete(oauthState);

        if (!oauthState.isProvider(provider) || oauthState.isExpired(Instant.now(clock))) {
            throw oauthFailed(provider);
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

    private DomainException oauthFailed(OAuthProvider provider) {
        return new DomainException(
            provider == OAuthProvider.GOOGLE
                ? ApiErrorCode.AUTH_GOOGLE_OAUTH_FAILED
                : ApiErrorCode.AUTH_GITHUB_OAUTH_FAILED
        );
    }
}
