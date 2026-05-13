package com.stackup.stackup.auth.application.dto;

public record GithubCallbackResult(
    String accessToken,
    String refreshToken,
    long userId,
    String githubUsername
) {
}
