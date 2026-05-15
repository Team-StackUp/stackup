package com.stackup.stackup.github.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubUserResponse(
    Long id,
    String login,
    String email,

    @JsonProperty("avatar_url")
    String avatarUrl
) {
}
