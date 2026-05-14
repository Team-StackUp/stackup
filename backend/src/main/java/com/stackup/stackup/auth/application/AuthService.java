package com.stackup.stackup.auth.application;

import com.stackup.stackup.auth.infrastructure.GithubOAuthClient;
import com.stackup.stackup.auth.application.dto.GithubCallbackResult;
import com.stackup.stackup.auth.application.dto.GithubLoginResult;
import com.stackup.stackup.auth.application.dto.GithubUserProfile;
import com.stackup.stackup.auth.application.dto.GithubUserUpsertResult;
import com.stackup.stackup.auth.application.dto.OAuthStateIssueResult;
import com.stackup.stackup.auth.application.dto.RefreshTokenResult;
import com.stackup.stackup.auth.application.dto.StreamTokenResult;
import com.stackup.stackup.common.config.properties.SecurityProperties;
import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.security.JwtTokenProvider;
import com.stackup.stackup.common.security.StreamTokenProvider;
import com.stackup.stackup.github.infrastructure.GithubApiClient;
import com.stackup.stackup.github.infrastructure.GithubTokenCipher;
import com.stackup.stackup.github.infrastructure.dto.GithubUserResponse;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String TOKEN_TYPE_BEARER = "Bearer";
    private static final long SKELETON_USER_ID = 1L;

    private final GithubOAuthClient githubOAuthClient;
    private final OAuthStateService oauthStateService;
    private final GithubApiClient githubApiClient;
    private final GithubTokenCipher githubTokenCipher;
    private final GithubUserService githubUserService;
    private final JwtTokenProvider jwtTokenProvider;
    private final StreamTokenProvider streamTokenProvider;
    private final SecurityProperties securityProperties;

    public AuthService(
        GithubOAuthClient githubOAuthClient,
        OAuthStateService oauthStateService,
        GithubApiClient githubApiClient,
        GithubTokenCipher githubTokenCipher,
        GithubUserService githubUserService,
        JwtTokenProvider jwtTokenProvider,
        StreamTokenProvider streamTokenProvider,
        SecurityProperties securityProperties
    ) {
        this.githubOAuthClient = githubOAuthClient;
        this.oauthStateService = oauthStateService;
        this.githubApiClient = githubApiClient;
        this.githubTokenCipher = githubTokenCipher;
        this.githubUserService = githubUserService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.streamTokenProvider = streamTokenProvider;
        this.securityProperties = securityProperties;
    }

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

        return new GithubCallbackResult(
            jwtTokenProvider.createAccessToken(upsertResult.user().id()),
            TOKEN_TYPE_BEARER,
            securityProperties.accessTokenTtlSeconds(),
            upsertResult.user(),
            upsertResult.newUser(),
            createRefreshTokenStub()
        );
    }

    public RefreshTokenResult refresh(String refreshToken) {
        return new RefreshTokenResult(
            jwtTokenProvider.createAccessToken(SKELETON_USER_ID),
            TOKEN_TYPE_BEARER,
            securityProperties.accessTokenTtlSeconds(),
            createRefreshTokenStub()
        );
    }

    public void logout(String refreshToken) {
        // Refresh token revocation is intentionally deferred to the auth implementation task.
    }

    public StreamTokenResult createStreamToken(Long userId) {
        if (userId == null) {
            throw new BadCredentialsException("Authentication is required to create stream token");
        }
        return new StreamTokenResult(streamTokenProvider.createStreamToken(userId));
    }

    private String createRefreshTokenStub() {
        return "refresh-token-stub-" + UUID.randomUUID();
    }
}
