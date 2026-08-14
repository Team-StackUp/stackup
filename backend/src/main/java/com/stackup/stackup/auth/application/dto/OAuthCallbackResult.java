package com.stackup.stackup.auth.application.dto;

public record OAuthCallbackResult(
    String accessToken,
    String tokenType,
    long expiresIn,
    AuthenticatedUserResult user,
    boolean isNewUser,
    String refreshTokenRawForCookie,
    long refreshTokenMaxAgeSeconds
) {
}
