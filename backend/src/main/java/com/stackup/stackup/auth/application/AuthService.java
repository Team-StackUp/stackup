package com.stackup.stackup.auth.application;

import com.stackup.stackup.user.domain.OAuthProvider;
import com.stackup.stackup.auth.infrastructure.GithubOAuthClient;
import com.stackup.stackup.auth.infrastructure.GoogleOAuthClient;
import com.stackup.stackup.auth.infrastructure.dto.GoogleUserInfoResponse;
import com.stackup.stackup.auth.application.dto.OAuthCallbackResult;
import com.stackup.stackup.auth.application.dto.OAuthLoginResult;
import com.stackup.stackup.auth.application.dto.GithubUserProfile;
import com.stackup.stackup.auth.application.dto.GoogleUserProfile;
import com.stackup.stackup.auth.application.dto.OAuthUserUpsertResult;
import com.stackup.stackup.auth.application.dto.OAuthStateIssueResult;
import com.stackup.stackup.auth.application.dto.RefreshTokenIssueResult;
import com.stackup.stackup.auth.application.dto.RefreshTokenResult;
import com.stackup.stackup.auth.application.dto.RefreshTokenRotationResult;
import com.stackup.stackup.auth.application.dto.StreamTokenResult;
import com.stackup.stackup.common.config.properties.SecurityProperties;
import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.security.JwtTokenProvider;
import com.stackup.stackup.common.security.StreamTokenProvider;
import com.stackup.stackup.github.infrastructure.GithubApiClient;
import com.stackup.stackup.github.infrastructure.GithubTokenCipher;
import com.stackup.stackup.github.infrastructure.dto.GithubUserResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
public record AuthService(
    GithubOAuthClient githubOAuthClient,
    GoogleOAuthClient googleOAuthClient,
    OAuthStateService oauthStateService,
    GithubApiClient githubApiClient,
    GithubTokenCipher githubTokenCipher,
    GithubUserService githubUserService,
    GoogleUserService googleUserService,
    RefreshTokenService refreshTokenService,
    JwtTokenProvider jwtTokenProvider,
    StreamTokenProvider streamTokenProvider,
    SecurityProperties securityProperties
) {

    public OAuthLoginResult startGithubLogin() {
        OAuthStateIssueResult oauthState = oauthStateService.issueStateWithPkce(OAuthProvider.GITHUB);
        return new OAuthLoginResult(
            githubOAuthClient.buildAuthorizationUrl(oauthState.state(), oauthState.codeChallenge()),
            oauthState.state()
        );
    }

    public OAuthCallbackResult completeGithubLogin(String code, String state) {
        if (code == null || code.isBlank()) {
            throw new DomainException(ApiErrorCode.AUTH_GITHUB_OAUTH_FAILED);
        }
        String codeVerifier = oauthStateService.consumeCodeVerifier(OAuthProvider.GITHUB, state);
        String githubAccessToken = githubOAuthClient.exchangeCode(code, codeVerifier);
        GithubUserResponse githubUser = githubApiClient.getUser(githubAccessToken);
        String email = githubUser.email() == null || githubUser.email().isBlank()
            ? githubApiClient.getPrimaryVerifiedEmail(githubAccessToken).orElse(null)
            : githubUser.email();
        String encryptedGithubAccessToken = githubTokenCipher.encrypt(githubAccessToken);
        OAuthUserUpsertResult upsertResult = githubUserService.upsertGithubUser(
            new GithubUserProfile(
                githubUser.id(),
                githubUser.login(),
                email,
                githubUser.avatarUrl()
            ),
            encryptedGithubAccessToken
        );
        RefreshTokenIssueResult refreshToken = refreshTokenService.issue(upsertResult.user().id());

        return new OAuthCallbackResult(
            jwtTokenProvider.createAccessToken(upsertResult.user().id()),
            securityProperties.accessTokenType(),
            securityProperties.accessTokenTtlSeconds(),
            upsertResult.user(),
            upsertResult.newUser(),
            refreshToken.rawToken(),
            refreshToken.maxAgeSeconds()
        );
    }

    public OAuthLoginResult startGoogleLogin() {
        OAuthStateIssueResult oauthState = oauthStateService.issueStateWithPkce(OAuthProvider.GOOGLE);
        return new OAuthLoginResult(
            googleOAuthClient.buildAuthorizationUrl(oauthState.state(), oauthState.codeChallenge()),
            oauthState.state()
        );
    }

    public OAuthCallbackResult completeGoogleLogin(String code, String state) {
        if (code == null || code.isBlank()) {
            throw new DomainException(ApiErrorCode.AUTH_GOOGLE_OAUTH_FAILED);
        }
        String codeVerifier = oauthStateService.consumeCodeVerifier(OAuthProvider.GOOGLE, state);
        String googleAccessToken = googleOAuthClient.exchangeCode(code, codeVerifier);
        GoogleUserInfoResponse profile = googleOAuthClient.fetchUserInfo(googleAccessToken);

        OAuthUserUpsertResult upsertResult = googleUserService.upsertGoogleUser(new GoogleUserProfile(
            profile.sub(),
            resolveDisplayName(profile),
            profile.email(),
            profile.picture()
        ));
        RefreshTokenIssueResult refreshToken = refreshTokenService.issue(upsertResult.user().id());

        return new OAuthCallbackResult(
            jwtTokenProvider.createAccessToken(upsertResult.user().id()),
            securityProperties.accessTokenType(),
            securityProperties.accessTokenTtlSeconds(),
            upsertResult.user(),
            upsertResult.newUser(),
            refreshToken.rawToken(),
            refreshToken.maxAgeSeconds()
        );
    }

    /**
     * Google 프로필에는 name 이 없을 수 있다(조직 정책 등). 이름이 비면 이메일 로컬파트로,
     * 그것도 없으면 고정 문구로 — display_name 은 NOT NULL 이고 화면에 항상 노출된다.
     */
    private String resolveDisplayName(GoogleUserInfoResponse profile) {
        if (profile.name() != null && !profile.name().isBlank()) {
            return truncate(profile.name());
        }
        if (profile.email() != null && !profile.email().isBlank()) {
            return truncate(profile.email().split("@")[0]);
        }
        return "사용자";
    }

    private String truncate(String value) {
        return value.length() <= 100 ? value : value.substring(0, 100);
    }

    public RefreshTokenResult refresh(String refreshToken) {
        RefreshTokenRotationResult rotation = refreshTokenService.rotate(refreshToken);
        return new RefreshTokenResult(
            jwtTokenProvider.createAccessToken(rotation.userId()),
            securityProperties.accessTokenType(),
            securityProperties.accessTokenTtlSeconds(),
            rotation.refreshToken().rawToken(),
            rotation.refreshToken().maxAgeSeconds()
        );
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    public StreamTokenResult createStreamToken(Long userId) {
        if (userId == null) {
            throw new BadCredentialsException("Authentication is required to create stream token");
        }
        return new StreamTokenResult(streamTokenProvider.createStreamToken(userId, "USER", userId));
    }

}
