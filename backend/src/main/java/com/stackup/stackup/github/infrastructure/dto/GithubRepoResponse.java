package com.stackup.stackup.github.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubRepoResponse(
    Long id,
    String name,

    @JsonProperty("full_name")
    String fullName,

    @JsonProperty("html_url")
    String htmlUrl,

    @JsonProperty("default_branch")
    String defaultBranch,

    @JsonProperty("private")
    Boolean isPrivate
) {
}
