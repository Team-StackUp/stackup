package com.stackup.stackup.auth.infrastructure;

import com.stackup.stackup.common.config.properties.GithubOAuthProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GithubOAuthClient {

    private final GithubOAuthProperties githubOAuthProperties;

    public GithubOAuthClient(GithubOAuthProperties githubOAuthProperties) {
        this.githubOAuthProperties = githubOAuthProperties;
    }

    public String buildAuthorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString("https://github.com/login/oauth/authorize")
            .queryParam("client_id", githubOAuthProperties.clientId())
            .queryParam("redirect_uri", githubOAuthProperties.redirectUri())
            .queryParam("scope", githubOAuthProperties.scopes())
            .queryParam("state", state)
            .build()
            .toUriString();
    }
}
