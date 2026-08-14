package com.stackup.stackup.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record OAuthCallbackResponse(
    @Schema(description = "StackUp JWT access token", example = "our-jwt-access-token", nullable = false)
    String accessToken,

    @Schema(description = "Access token type", example = "Bearer", nullable = false)
    String tokenType,

    @Schema(description = "Access token TTL in seconds", example = "900", nullable = false)
    long expiresIn,

    @Schema(description = "Authenticated user profile", nullable = false)
    AuthUserResponse user,

    @Schema(description = "Whether a new user was created during this login", example = "false", nullable = false)
    boolean isNewUser
) {
}
