package com.stackup.stackup.auth.application.dto;

public record AuthenticatedUserResult(
    long id,
    long githubId,
    String githubUsername,
    String email,
    String avatarUrl
) {
}
