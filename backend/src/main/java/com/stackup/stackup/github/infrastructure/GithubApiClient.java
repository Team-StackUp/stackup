package com.stackup.stackup.github.infrastructure;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.github.infrastructure.dto.GithubEmailResponse;
import com.stackup.stackup.github.infrastructure.dto.GithubRepoResponse;
import com.stackup.stackup.github.infrastructure.dto.GithubUserResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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

    public GithubRepoResponse getRepository(String accessToken, String fullName) {
        String[] parts = parseFullName(fullName);
        try {
            GithubRepoResponse response = githubApiHttpClient.getRepository(bearer(accessToken), parts[0], parts[1]);
            if (response == null || response.id() == null || response.fullName() == null) {
                throw repoApiFailed();
            }
            return response;
        } catch (HttpClientErrorException exception) {
            HttpStatusCode status = exception.getStatusCode();
            if (status.value() == 404) {
                throw new DomainException(ApiErrorCode.REPO_NOT_FOUND);
            }
            if (status.value() == 403) {
                throw new DomainException(ApiErrorCode.REPO_PRIVATE_NO_ACCESS);
            }
            throw repoApiFailed(exception);
        } catch (RestClientException exception) {
            throw repoApiFailed(exception);
        }
    }

    public List<GithubRepoResponse> listUserRepositories(String accessToken, int page, int perPage) {
        try {
            GithubRepoResponse[] response = githubApiHttpClient.listUserRepositories(
                bearer(accessToken), perPage, page, "updated", "owner"
            );
            if (response == null) {
                return List.of();
            }
            return Arrays.asList(response);
        } catch (RestClientException exception) {
            throw repoApiFailed(exception);
        }
    }

    private String[] parseFullName(String fullName) {
        if (fullName == null) {
            throw new DomainException(ApiErrorCode.VALIDATION_ERROR);
        }
        int slash = fullName.indexOf('/');
        if (slash <= 0 || slash == fullName.length() - 1) {
            throw new DomainException(ApiErrorCode.VALIDATION_ERROR);
        }
        return new String[]{fullName.substring(0, slash), fullName.substring(slash + 1)};
    }

    private DomainException repoApiFailed() {
        return new DomainException(ApiErrorCode.REPO_GITHUB_API_FAILED);
    }

    private DomainException repoApiFailed(Throwable cause) {
        return new DomainException(
            ApiErrorCode.REPO_GITHUB_API_FAILED,
            ApiErrorCode.REPO_GITHUB_API_FAILED.getDefaultMessage(),
            cause
        );
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
