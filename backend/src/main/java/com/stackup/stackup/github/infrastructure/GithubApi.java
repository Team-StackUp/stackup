package com.stackup.stackup.github.infrastructure;

import com.stackup.stackup.github.infrastructure.dto.GithubEmailResponse;
import com.stackup.stackup.github.infrastructure.dto.GithubRepoResponse;
import com.stackup.stackup.github.infrastructure.dto.GithubUserResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface GithubApi {

    @GetExchange("/user")
    GithubUserResponse getUser(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @GetExchange("/user/emails")
    GithubEmailResponse[] getEmails(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @GetExchange("/repositories/{id}")
    GithubRepoResponse getRepository(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable long id
    );

    @GetExchange("/user/repos")
    ResponseEntity<GithubRepoResponse[]> listUserRepos(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
        @RequestParam("per_page") int perPage,
        @RequestParam("page") int page,
        @RequestParam("sort") String sort,
        @RequestParam("affiliation") String affiliation
    );
}
