package com.stackup.stackup.auth.application.dto;

public record OAuthUserUpsertResult(
    AuthenticatedUserResult user,
    boolean newUser
) {
}
