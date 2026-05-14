package com.stackup.stackup.auth.application;

import com.stackup.stackup.auth.infrastructure.GithubOAuthClient;
import com.stackup.stackup.auth.application.dto.AuthenticatedUserResult;
import com.stackup.stackup.auth.application.dto.GithubCallbackResult;
import com.stackup.stackup.auth.application.dto.GithubLoginResult;
import com.stackup.stackup.auth.application.dto.RefreshTokenResult;
import com.stackup.stackup.auth.application.dto.StreamTokenResult;
import com.stackup.stackup.common.config.properties.SecurityProperties;
import com.stackup.stackup.common.security.JwtTokenProvider;
import com.stackup.stackup.common.security.StreamTokenProvider;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final long SKELETON_USER_ID = 1L;
    private static final long SKELETON_GITHUB_ID = 123456L;
    private static final String SKELETON_GITHUB_USERNAME = "stackup-user";
    private static final String TOKEN_TYPE_BEARER = "Bearer";

    private final GithubOAuthClient githubOAuthClient;
    private final JwtTokenProvider jwtTokenProvider;
    private final StreamTokenProvider streamTokenProvider;
    private final SecurityProperties securityProperties;

    public AuthService(
        GithubOAuthClient githubOAuthClient,
        JwtTokenProvider jwtTokenProvider,
        StreamTokenProvider streamTokenProvider,
        SecurityProperties securityProperties
    ) {
        this.githubOAuthClient = githubOAuthClient;
        this.jwtTokenProvider = jwtTokenProvider;
        this.streamTokenProvider = streamTokenProvider;
        this.securityProperties = securityProperties;
    }

    public GithubLoginResult startGithubLogin() {
        String state = UUID.randomUUID().toString();
        return new GithubLoginResult(githubOAuthClient.buildAuthorizationUrl(state), state);
    }

    public GithubCallbackResult completeGithubLogin(String code, String state) {
        return new GithubCallbackResult(
            jwtTokenProvider.createAccessToken(SKELETON_USER_ID),
            TOKEN_TYPE_BEARER,
            securityProperties.accessTokenTtlSeconds(),
            new AuthenticatedUserResult(
                SKELETON_USER_ID,
                SKELETON_GITHUB_ID,
                SKELETON_GITHUB_USERNAME,
                "stackup-user@example.com",
                "https://avatars.githubusercontent.com/u/123456"
            ),
            true,
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
