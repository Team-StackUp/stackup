package com.stackup.stackup.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RefreshTokenResponse(
    @Schema(description = "New StackUp JWT access token", example = "new-our-jwt-access-token", nullable = false)
    String accessToken,

    @Schema(description = "Access token type", example = "Bearer", nullable = false)
    String tokenType,

    @Schema(description = "Access token TTL in seconds", example = "900", nullable = false)
    long expiresIn
) {
}
