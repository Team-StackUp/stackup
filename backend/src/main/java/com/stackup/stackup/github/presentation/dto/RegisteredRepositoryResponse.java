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
    Instant createdAt
) {
    public static RegisteredRepositoryResponse from(GithubRepositoryResult r) {
        return new RegisteredRepositoryResponse(
            r.id(), r.githubRepoId(), r.repoName(), r.repoFullName(),
            r.repoUrl(), r.defaultBranch(), r.status(),
            r.lastSyncedAt(), r.createdAt()
        );
    }
}
