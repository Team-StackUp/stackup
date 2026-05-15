package com.stackup.stackup.auth.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubTokenResponse(
    @JsonProperty("access_token")
    String accessToken,

    @JsonProperty("token_type")
    String tokenType,

    String scope,

    String error,

    @JsonProperty("error_description")
    String errorDescription
) {
}
