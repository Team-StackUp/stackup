package com.stackup.stackup.auth.application.dto;

public record GoogleUserProfile(
    String googleId,
    String displayName,
    String email,
    String avatarUrl
) {
}
