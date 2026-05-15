package com.stackup.stackup.auth.infrastructure;

import com.stackup.stackup.auth.infrastructure.dto.GithubTokenResponse;
import com.stackup.stackup.common.config.properties.GithubOAuthProperties;
import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GithubOAuthClient {

    private final GithubOAuthProperties githubOAuthProperties;
    private final RestClient restClient;

    public GithubOAuthClient(GithubOAuthProperties githubOAuthProperties) {
        this.githubOAuthProperties = githubOAuthProperties;
        this.restClient = RestClient.builder().build();
    }

    public String buildAuthorizationUrl(String state, String codeChallenge) {
        return UriComponentsBuilder.fromUri(githubOAuthProperties.authorizationUrl())
            .queryParam("client_id", githubOAuthProperties.clientId())
            .queryParam("redirect_uri", githubOAuthProperties.redirectUri())
            .queryParam("scope", githubOAuthProperties.scopes())
            .queryParam("state", state)
            .queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", githubOAuthProperties.codeChallengeMethod())
            .build()
            .toUriString();
    }

    public String exchangeCode(String code, String codeVerifier) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", githubOAuthProperties.clientId());
        body.add("client_secret", githubOAuthProperties.clientSecret());
        body.add("code", code);
        body.add("redirect_uri", githubOAuthProperties.redirectUri().toString());
        body.add("code_verifier", codeVerifier);

        try {
            GithubTokenResponse response = restClient.post()
                .uri(githubOAuthProperties.tokenUrl())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(GithubTokenResponse.class);

            return validateTokenResponse(response);
        } catch (RestClientException exception) {
            throw oauthFailed(exception);
        }
    }

    private String validateTokenResponse(GithubTokenResponse response) {
        if (response == null || response.error() != null || response.accessToken() == null
            || response.accessToken().isBlank()) {
            throw oauthFailed();
        }
        if (response.tokenType() == null || !githubOAuthProperties.tokenType().equalsIgnoreCase(response.tokenType())) {
            throw oauthFailed();
        }
        if (!hasRequiredScopes(response.scope())) {
            throw oauthFailed();
        }
        return response.accessToken();
    }

    private boolean hasRequiredScopes(String grantedScopes) {
        if (grantedScopes == null || grantedScopes.isBlank()) {
            return false;
        }

        Set<String> granted = Arrays.stream(grantedScopes.split("[,\\s]+"))
            .filter(scope -> !scope.isBlank())
            .collect(Collectors.toSet());

        return Arrays.stream(githubOAuthProperties.scopes().split("\\s+"))
            .filter(scope -> !scope.isBlank())
            .allMatch(granted::contains);
    }

    private DomainException oauthFailed() {
        return new DomainException(ApiErrorCode.AUTH_GITHUB_OAUTH_FAILED);
    }

    private DomainException oauthFailed(Throwable cause) {
        return new DomainException(
            ApiErrorCode.AUTH_GITHUB_OAUTH_FAILED,
            ApiErrorCode.AUTH_GITHUB_OAUTH_FAILED.getDefaultMessage(),
            cause
        );
    }
}
