package com.stackup.stackup.auth.presentation.dto;

public record GithubLoginResponse(
    String authorizationUrl,
    String state
) {
}
