package com.stackup.stackup.github.infrastructure;

import com.stackup.stackup.github.infrastructure.dto.GithubEmailResponse;
import com.stackup.stackup.github.infrastructure.dto.GithubUserResponse;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;

public interface GithubApiHttpClient {

    @GetExchange("/user")
    GithubUserResponse getUser(@RequestHeader("Authorization") String authorization);

    @GetExchange("/user/emails")
    GithubEmailResponse[] getUserEmails(@RequestHeader("Authorization") String authorization);
}
