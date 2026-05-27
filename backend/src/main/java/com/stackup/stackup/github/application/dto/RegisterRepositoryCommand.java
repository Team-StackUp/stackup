package com.stackup.stackup.github.application.dto;

public record RegisterRepositoryCommand(
    Long githubRepoId,
    String fullName
) {
}
