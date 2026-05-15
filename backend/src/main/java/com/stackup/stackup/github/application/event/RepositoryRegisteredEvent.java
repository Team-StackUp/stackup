package com.stackup.stackup.github.application.event;

public record RepositoryRegisteredEvent(
    Long repositoryId,
    Long userId,
    String githubFullName,
    String defaultBranch,
    String encryptedGithubAccessToken
) {
}
