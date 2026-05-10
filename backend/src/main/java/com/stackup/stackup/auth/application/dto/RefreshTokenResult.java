package com.stackup.stackup.auth.application.dto;

public record RefreshTokenResult(
    String accessToken,
    String refreshToken
) {
}
