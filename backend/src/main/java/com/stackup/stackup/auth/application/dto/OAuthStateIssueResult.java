package com.stackup.stackup.auth.application.dto;

public record OAuthStateIssueResult(
    String state,
    String codeChallenge
) {
}
