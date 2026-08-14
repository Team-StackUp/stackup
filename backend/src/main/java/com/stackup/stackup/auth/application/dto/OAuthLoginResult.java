package com.stackup.stackup.auth.application.dto;

public record GithubLoginResult(
    String authorizationUrl,
    String state
) {
}
