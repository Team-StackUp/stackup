package com.stackup.stackup.github.infrastructure;

import com.stackup.stackup.common.config.properties.GithubOAuthProperties;
import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.github.infrastructure.dto.GithubEmailResponse;
import com.stackup.stackup.github.infrastructure.dto.GithubRepoListPage;
import com.stackup.stackup.github.infrastructure.dto.GithubRepoResponse;
import com.stackup.stackup.github.infrastructure.dto.GithubUserResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GithubApiClient {

    private final RestClient restClient;

    @Autowired
    public GithubApiClient(GithubOAuthProperties githubOAuthProperties) {
        this(githubOAuthProperties, RestClient.builder());
    }

    public GithubApiClient(GithubOAuthProperties githubOAuthProperties, RestClient.Builder builder) {
        this.restClient = builder
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

    public GithubRepoResponse getRepository(String accessToken, long githubRepoId) {
        try {
            GithubRepoResponse body = restClient.get()
                .uri("/repositories/{id}", githubRepoId)
                .headers(h -> h.setBearerAuth(accessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(GithubRepoResponse.class);

            if (body == null || body.id() == null) {
                throw new DomainException(ApiErrorCode.REPO_GITHUB_API_FAILED);
            }
            return body;
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            throw new DomainException(ApiErrorCode.REPO_PRIVATE_NO_ACCESS);
        } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
            String remaining = e.getResponseHeaders() == null ? null
                : e.getResponseHeaders().getFirst("X-RateLimit-Remaining");
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
            ResponseEntity<GithubRepoResponse[]> response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/user/repos")
                    .queryParam("per_page", perPage)
                    .queryParam("page", page)
                    .queryParam("sort", "updated")
                    .queryParam("affiliation", "owner,collaborator,organization_member")
                    .build())
                .headers(h -> h.setBearerAuth(accessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(GithubRepoResponse[].class);

            GithubRepoResponse[] body = response.getBody();
            List<GithubRepoResponse> repos = body == null ? List.of() : Arrays.asList(body);
            boolean hasNext = parseLinkHasNext(response.getHeaders().getFirst("Link"));
            return new GithubRepoListPage(repos, hasNext);
        } catch (RestClientException exception) {
            throw githubApiFailed(exception);
        }
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
