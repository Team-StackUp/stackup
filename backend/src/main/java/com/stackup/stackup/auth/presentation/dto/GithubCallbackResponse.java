package com.stackup.stackup.auth.presentation.dto;

public record GithubCallbackResponse(
    String accessToken,
    String refreshToken,
    long userId,
    String githubUsername
) {
}
