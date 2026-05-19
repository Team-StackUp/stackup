package com.stackup.stackup.github.infrastructure.dto;

public record AnalyzeRepositoryPayload(
    Long repositoryId,
    String githubFullName,
    String defaultBranch,
    String githubAccessTokenEncrypted
) {
}
