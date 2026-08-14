package com.stackup.stackup.auth.application.dto;

public record OAuthLoginResult(
    String authorizationUrl,
    String state
) {
}
