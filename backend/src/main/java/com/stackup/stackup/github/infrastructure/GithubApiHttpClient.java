package com.stackup.stackup.github.infrastructure;

import com.stackup.stackup.github.infrastructure.dto.GithubEmailResponse;
import com.stackup.stackup.github.infrastructure.dto.GithubRepoResponse;
import com.stackup.stackup.github.infrastructure.dto.GithubUserResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

public interface GithubApiHttpClient {

    @GetExchange("/user")
    GithubUserResponse getUser(@RequestHeader("Authorization") String authorization);

    @GetExchange("/user/emails")
    GithubEmailResponse[] getUserEmails(@RequestHeader("Authorization") String authorization);

    @GetExchange("/repos/{owner}/{repo}")
    GithubRepoResponse getRepository(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("owner") String owner,
        @PathVariable("repo") String repo
    );

    @GetExchange("/user/repos")
    GithubRepoResponse[] listUserRepositories(
        @RequestHeader("Authorization") String authorization,
        @RequestParam("per_page") int perPage,
        @RequestParam("page") int page,
        @RequestParam("sort") String sort,
        @RequestParam("affiliation") String affiliation
    );
}
