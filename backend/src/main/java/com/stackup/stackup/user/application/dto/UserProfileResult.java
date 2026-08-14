package com.stackup.stackup.user.application.dto;

import com.stackup.stackup.user.domain.OAuthProvider;

public record UserProfileResult(
    long id,
    OAuthProvider provider,
    String displayName,
    Long githubId,
    String githubUsername,
    String email,
    String avatarUrl
) {
}
