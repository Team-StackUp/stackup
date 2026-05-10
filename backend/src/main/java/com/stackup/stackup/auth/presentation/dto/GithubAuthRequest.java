package com.stackup.stackup.auth.presentation.dto;

public record GithubAuthRequest(
    String code,
    String state
) {
}
