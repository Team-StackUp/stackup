package com.stackup.stackup.auth.application.dto;

public record RefreshTokenIssueResult(
    String rawToken,
    long maxAgeSeconds
) {
}
