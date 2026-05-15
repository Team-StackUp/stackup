package com.stackup.stackup.user.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserProfileResponse(
    @Schema(description = "StackUp user id", example = "1", nullable = false)
    long id,

    @Schema(description = "Stable GitHub user id", example = "123456", nullable = false)
    long githubId,

    @Schema(description = "GitHub username at login time", example = "octocat", nullable = false)
    String githubUsername,

    @Schema(description = "Primary GitHub email when available", example = "octocat@example.com", nullable = true)
    String email,

    @Schema(description = "GitHub avatar URL", example = "https://avatars.githubusercontent.com/u/123456", nullable = true)
    String avatarUrl
) {
}
