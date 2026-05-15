package com.stackup.stackup.github.infrastructure.dto;

public record GithubEmailResponse(
    String email,
    boolean primary,
    boolean verified,
    String visibility
) {
}
