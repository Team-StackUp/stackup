package com.stackup.stackup.user.presentation.dto;

import com.stackup.stackup.user.domain.OAuthProvider;
import com.stackup.stackup.user.application.dto.UserProfileResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserProfileResponse(
    @Schema(description = "StackUp user id", example = "1", nullable = false)
    long id,

    @Schema(description = "OAuth provider this account was created with", example = "GITHUB", nullable = false)
    OAuthProvider provider,

    @Schema(description = "Name shown in the UI", example = "octocat", nullable = false)
    String displayName,

    @Schema(description = "Stable GitHub user id. Null for Google accounts", example = "123456", nullable = true)
    Long githubId,

    @Schema(description = "GitHub username at login time. Null for Google accounts", example = "octocat", nullable = true)
    String githubUsername,

    @Schema(description = "Primary email when available", example = "octocat@example.com", nullable = true)
    String email,

    @Schema(description = "Avatar URL", example = "https://avatars.githubusercontent.com/u/123456", nullable = true)
    String avatarUrl
) {

    public static UserProfileResponse from(UserProfileResult result) {
        return new UserProfileResponse(
            result.id(),
            result.provider(),
            result.displayName(),
            result.githubId(),
            result.githubUsername(),
            result.email(),
            result.avatarUrl()
        );
    }
}
