package com.stackup.stackup.github.presentation.dto;

import com.stackup.stackup.github.application.dto.GithubRepositoryResult;
import com.stackup.stackup.github.domain.RepositoryStatus;
import java.time.Instant;

public record RegisteredRepositoryResponse(
    Long id,
    Long githubRepoId,
    String repoName,
    String repoFullName,
    String repoUrl,
    String defaultBranch,
    RepositoryStatus status,
    Instant lastSyncedAt,
    Instant createdAt,
    Instant updatedAt
) {
    public static RegisteredRepositoryResponse from(GithubRepositoryResult result) {
        return new RegisteredRepositoryResponse(
            result.id(),
            result.githubRepoId(),
            result.repoName(),
            result.repoFullName(),
            result.repoUrl(),
            result.defaultBranch(),
            result.status(),
            result.lastSyncedAt(),
            result.createdAt(),
            result.updatedAt()
        );
    }
}
