package com.stackup.stackup.auth.infrastructure;

import com.stackup.stackup.auth.infrastructure.dto.GoogleTokenResponse;
import com.stackup.stackup.auth.infrastructure.dto.GoogleUserInfoResponse;
import com.stackup.stackup.common.config.properties.GoogleOAuthProperties;
import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GoogleOAuthClient {

    private static final String BEARER_PREFIX = "Bearer ";

    private final GoogleOAuthProperties googleOAuthProperties;
    private final GoogleOAuthHttpClient googleOAuthHttpClient;
    private final GoogleUserInfoHttpClient googleUserInfoHttpClient;

    public GoogleOAuthClient(
        GoogleOAuthProperties googleOAuthProperties,
        GoogleOAuthHttpClient googleOAuthHttpClient,
        GoogleUserInfoHttpClient googleUserInfoHttpClient
    ) {
        this.googleOAuthProperties = googleOAuthProperties;
        this.googleOAuthHttpClient = googleOAuthHttpClient;
        this.googleUserInfoHttpClient = googleUserInfoHttpClient;
    }

    public String buildAuthorizationUrl(String state, String codeChallenge) {
        requireConfigured();
        return UriComponentsBuilder.fromUri(googleOAuthProperties.authorizationBaseUrl())
            .queryParam("client_id", googleOAuthProperties.clientId())
            .queryParam("redirect_uri", googleOAuthProperties.redirectUri())
            .queryParam("response_type", "code")
            .queryParam("scope", googleOAuthProperties.scopes())
            .queryParam("state", state)
            .queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", googleOAuthProperties.codeChallengeMethod())
            // 재동의 없이도 항상 프로필을 받도록. offline access(refresh token)는 쓰지 않는다 —
            // StackUp 은 Google API 를 대신 호출할 일이 없고 로그인 신원만 필요하다.
            .queryParam("prompt", "select_account")
            .build()
            .toUriString();
    }

    public String exchangeCode(String code, String codeVerifier) {
        requireConfigured();
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", googleOAuthProperties.clientId());
        body.add("client_secret", googleOAuthProperties.clientSecret());
        body.add("code", code);
        body.add("grant_type", "authorization_code");
        body.add("redirect_uri", googleOAuthProperties.redirectUri().toString());
        body.add("code_verifier", codeVerifier);

        try {
            return validateTokenResponse(googleOAuthHttpClient.exchangeCode(body));
        } catch (RestClientException exception) {
            throw oauthFailed(exception);
        }
    }

    /**
     * 액세스 토큰으로 프로필을 읽는다.
     *
     * id_token 을 직접 파싱하지 않는 이유 — 서명 검증에 JWKS 캐싱·키 롤오버 처리가 따라붙는다.
     * userinfo 는 우리가 TLS 로 직접 호출하므로 응답 출처가 보장되고, 한 번의 왕복만 더 든다.
     */
    public GoogleUserInfoResponse fetchUserInfo(String accessToken) {
        try {
            GoogleUserInfoResponse userInfo =
                googleUserInfoHttpClient.getUserInfo(BEARER_PREFIX + accessToken);
            if (userInfo == null || userInfo.sub() == null || userInfo.sub().isBlank()) {
                throw oauthFailed();
            }
            return userInfo;
        } catch (RestClientException exception) {
            throw oauthFailed(exception);
        }
    }

    private String validateTokenResponse(GoogleTokenResponse response) {
        if (response == null || response.error() != null
            || response.accessToken() == null || response.accessToken().isBlank()) {
            throw oauthFailed();
        }
        return response.accessToken();
    }

    // 시크릿 미설정 상태에서 Google 로그인을 시도하면 Google 이 주는 모호한 오류 대신
    // 우리 쪽 설정 문제임을 분명히 남긴다.
    private void requireConfigured() {
        if (!googleOAuthProperties.isConfigured()) {
            throw new DomainException(
                ApiErrorCode.AUTH_GOOGLE_OAUTH_FAILED,
                "Google 로그인이 아직 설정되지 않았습니다."
            );
        }
    }

    private DomainException oauthFailed() {
        return new DomainException(ApiErrorCode.AUTH_GOOGLE_OAUTH_FAILED);
    }

    private DomainException oauthFailed(Throwable cause) {
        return new DomainException(
            ApiErrorCode.AUTH_GOOGLE_OAUTH_FAILED,
            ApiErrorCode.AUTH_GOOGLE_OAUTH_FAILED.getDefaultMessage(),
            cause
        );
    }
}
