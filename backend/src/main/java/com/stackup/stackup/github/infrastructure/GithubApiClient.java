package com.stackup.stackup.github.infrastructure;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.github.infrastructure.dto.GithubEmailResponse;
import com.stackup.stackup.github.infrastructure.dto.GithubUserResponse;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class GithubApiClient {

    private final GithubApiHttpClient githubApiHttpClient;

    public GithubApiClient(GithubApiHttpClient githubApiHttpClient) {
        this.githubApiHttpClient = githubApiHttpClient;
    }

    public GithubUserResponse getUser(String githubAccessToken) {
        try {
            GithubUserResponse response = githubApiHttpClient.getUser(bearer(githubAccessToken));

            if (response == null || response.id() == null || response.login() == null || response.login().isBlank()) {
                throw oauthFailed();
            }
            return response;
        } catch (RestClientException exception) {
            throw oauthFailed(exception);
        }
    }

    public Optional<String> getPrimaryVerifiedEmail(String githubAccessToken) {
        try {
            GithubEmailResponse[] response = githubApiHttpClient.getUserEmails(bearer(githubAccessToken));

            if (response == null) {
                return Optional.empty();
            }

            return Arrays.stream(response)
                .filter(GithubEmailResponse::primary)
                .filter(GithubEmailResponse::verified)
                .map(GithubEmailResponse::email)
                .filter(email -> email != null && !email.isBlank())
                .findFirst();
        } catch (RestClientException exception) {
            throw oauthFailed(exception);
        }
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
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
