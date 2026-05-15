package com.stackup.stackup.auth.application;

import com.stackup.stackup.auth.infrastructure.GithubOAuthClient;
import com.stackup.stackup.auth.application.dto.GithubCallbackResult;
import com.stackup.stackup.auth.application.dto.GithubLoginResult;
import com.stackup.stackup.auth.application.dto.GithubUserProfile;
import com.stackup.stackup.auth.application.dto.GithubUserUpsertResult;
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
    OAuthStateService oauthStateService,
    GithubApiClient githubApiClient,
    GithubTokenCipher githubTokenCipher,
    GithubUserService githubUserService,
    RefreshTokenService refreshTokenService,
    JwtTokenProvider jwtTokenProvider,
    StreamTokenProvider streamTokenProvider,
    SecurityProperties securityProperties
) {

    public GithubLoginResult startGithubLogin() {
        OAuthStateIssueResult oauthState = oauthStateService.issueGithubStateWithPkce();
        return new GithubLoginResult(
            githubOAuthClient.buildAuthorizationUrl(oauthState.state(), oauthState.codeChallenge()),
            oauthState.state()
        );
    }

    public GithubCallbackResult completeGithubLogin(String code, String state) {
        if (code == null || code.isBlank()) {
            throw new DomainException(ApiErrorCode.AUTH_GITHUB_OAUTH_FAILED);
        }
        String codeVerifier = oauthStateService.consumeGithubCodeVerifier(state);
        String githubAccessToken = githubOAuthClient.exchangeCode(code, codeVerifier);
        GithubUserResponse githubUser = githubApiClient.getUser(githubAccessToken);
        String email = githubUser.email() == null || githubUser.email().isBlank()
            ? githubApiClient.getPrimaryVerifiedEmail(githubAccessToken).orElse(null)
            : githubUser.email();
        String encryptedGithubAccessToken = githubTokenCipher.encrypt(githubAccessToken);
        GithubUserUpsertResult upsertResult = githubUserService.upsertGithubUser(
            new GithubUserProfile(
                githubUser.id(),
                githubUser.login(),
                email,
                githubUser.avatarUrl()
            ),
            encryptedGithubAccessToken
        );
        RefreshTokenIssueResult refreshToken = refreshTokenService.issue(upsertResult.user().id());

        return new GithubCallbackResult(
            jwtTokenProvider.createAccessToken(upsertResult.user().id()),
            securityProperties.accessTokenType(),
            securityProperties.accessTokenTtlSeconds(),
            upsertResult.user(),
            upsertResult.newUser(),
            refreshToken.rawToken(),
            refreshToken.maxAgeSeconds()
        );
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
        return new StreamTokenResult(streamTokenProvider.createStreamToken(userId));
    }

}
