package com.stackup.stackup.github.infrastructure;

import com.stackup.stackup.common.config.properties.GithubOAuthProperties;
import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.github.infrastructure.dto.GithubEmailResponse;
import com.stackup.stackup.github.infrastructure.dto.GithubUserResponse;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GithubApiClient {

    private final RestClient restClient;

    public GithubApiClient(GithubOAuthProperties githubOAuthProperties) {
        this.restClient = RestClient.builder()
            .baseUrl(githubOAuthProperties.apiBaseUrl())
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", githubOAuthProperties.apiVersion())
            .build();
    }

    public GithubUserResponse getUser(String githubAccessToken) {
        try {
            GithubUserResponse response = restClient.get()
                .uri("/user")
                .headers(headers -> headers.setBearerAuth(githubAccessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(GithubUserResponse.class);

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
            GithubEmailResponse[] response = restClient.get()
                .uri("/user/emails")
                .headers(headers -> headers.setBearerAuth(githubAccessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(GithubEmailResponse[].class);

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
