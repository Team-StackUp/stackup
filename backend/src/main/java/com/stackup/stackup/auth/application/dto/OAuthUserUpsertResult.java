package com.stackup.stackup.auth.application.dto;

public record GithubUserUpsertResult(
    AuthenticatedUserResult user,
    boolean newUser
) {
}
