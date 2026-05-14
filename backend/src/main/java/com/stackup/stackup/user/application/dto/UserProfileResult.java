package com.stackup.stackup.user.application.dto;

public record UserProfileResult(
    long id,
    long githubId,
    String githubUsername,
    String email,
    String avatarUrl
) {
}
