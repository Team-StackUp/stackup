package com.stackup.stackup.auth.application.dto;

public record GithubUserProfile(
    Long githubId,
    String githubUsername,
    String email,
    String avatarUrl
) {
}
