package com.stackup.stackup.auth.presentation.dto;

public record RefreshTokenResponse(
    String accessToken,
    String refreshToken
) {
}
