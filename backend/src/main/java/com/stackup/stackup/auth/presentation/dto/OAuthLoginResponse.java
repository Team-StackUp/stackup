package com.stackup.stackup.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record GithubLoginResponse(
    @Schema(description = "GitHub OAuth authorization URL", example = "https://github.com/login/oauth/authorize?client_id=...&redirect_uri=...&scope=read:user%20user:email%20repo&state=generated-state", nullable = false)
    String authorizationUrl,

    @Schema(description = "CSRF protection state value to be verified on callback", example = "generated-state", nullable = false)
    String state
) {
}
