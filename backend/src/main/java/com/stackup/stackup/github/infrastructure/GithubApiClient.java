package com.stackup.stackup.github.infrastructure;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.github.infrastructure.dto.GithubEmailResponse;
import com.stackup.stackup.github.infrastructure.dto.GithubRepoListPage;
import com.stackup.stackup.github.infrastructure.dto.GithubRepoResponse;
import com.stackup.stackup.github.infrastructure.dto.GithubUserResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class GithubApiClient {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SORT_UPDATED = "updated";
    private static final String AFFILIATION = "owner,collaborator,organization_member";
    private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String LINK_HEADER = "Link";

    private final GithubApi githubApi;

    public GithubUserResponse getUser(String githubAccessToken) {
        try {
            GithubUserResponse response = githubApi.getUser(bearer(githubAccessToken));
            if (response == null || response.id() == null
                || response.login() == null || response.login().isBlank()) {
                throw oauthFailed();
            }
            return response;
        } catch (RestClientException exception) {
            throw oauthFailed(exception);
        }
    }

    public Optional<String> getPrimaryVerifiedEmail(String githubAccessToken) {
        try {
            GithubEmailResponse[] response = githubApi.getEmails(bearer(githubAccessToken));
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

    public GithubRepoResponse getRepository(String accessToken, long githubRepoId) {
        try {
            GithubRepoResponse body = githubApi.getRepository(bearer(accessToken), githubRepoId);
            if (body == null || body.id() == null) {
                throw new DomainException(ApiErrorCode.REPO_GITHUB_API_FAILED);
            }
            return body;
        } catch (HttpClientErrorException.NotFound e) {
            throw new DomainException(ApiErrorCode.REPO_PRIVATE_NO_ACCESS);
        } catch (HttpClientErrorException.Forbidden e) {
            String remaining = e.getResponseHeaders() == null ? null
                : e.getResponseHeaders().getFirst(RATE_LIMIT_REMAINING_HEADER);
            throw "0".equals(remaining)
                ? new DomainException(ApiErrorCode.SYS_RATE_LIMITED)
                : new DomainException(ApiErrorCode.REPO_PRIVATE_NO_ACCESS);
        } catch (DomainException e) {
            throw e;
        } catch (RestClientException exception) {
            throw githubApiFailed(exception);
        }
    }

    public GithubRepoListPage listUserRepositories(String accessToken, int page, int perPage) {
        try {
            ResponseEntity<GithubRepoResponse[]> response = githubApi.listUserRepos(
                bearer(accessToken), perPage, page, SORT_UPDATED, AFFILIATION
            );
            GithubRepoResponse[] body = response.getBody();
            List<GithubRepoResponse> repos = body == null ? List.of() : Arrays.asList(body);
            boolean hasNext = parseLinkHasNext(response.getHeaders().getFirst(LINK_HEADER));
            return new GithubRepoListPage(repos, hasNext);
        } catch (RestClientException exception) {
            throw githubApiFailed(exception);
        }
    }

    private static String bearer(String token) {
        return BEARER_PREFIX + token;
    }

    private static boolean parseLinkHasNext(String linkHeader) {
        if (linkHeader == null || linkHeader.isBlank()) {
            return false;
        }
        return Arrays.stream(linkHeader.split(","))
            .map(String::trim)
            .anyMatch(seg -> seg.endsWith("rel=\"next\""));
    }

    private DomainException githubApiFailed(Throwable cause) {
        return new DomainException(
            ApiErrorCode.REPO_GITHUB_API_FAILED,
            ApiErrorCode.REPO_GITHUB_API_FAILED.getDefaultMessage(),
            cause
        );
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
