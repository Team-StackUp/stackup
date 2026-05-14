package com.stackup.stackup.auth.application.dto;

public record RefreshTokenResult(
    String accessToken,
    String tokenType,
    long expiresIn,
    String refreshTokenRawForCookie,
    long refreshTokenMaxAgeSeconds
) {
}
