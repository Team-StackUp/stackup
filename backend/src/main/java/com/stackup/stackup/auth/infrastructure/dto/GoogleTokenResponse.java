package com.stackup.stackup.auth.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GoogleTokenResponse(
    @JsonProperty("access_token")
    String accessToken,

    @JsonProperty("token_type")
    String tokenType,

    @JsonProperty("expires_in")
    Long expiresIn,

    @JsonProperty("id_token")
    String idToken,

    String scope,

    String error,

    @JsonProperty("error_description")
    String errorDescription
) {
}
