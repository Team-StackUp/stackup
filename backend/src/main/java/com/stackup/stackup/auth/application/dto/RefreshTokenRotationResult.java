package com.stackup.stackup.auth.application.dto;

public record RefreshTokenRotationResult(
    Long userId,
    RefreshTokenIssueResult refreshToken
) {
}
