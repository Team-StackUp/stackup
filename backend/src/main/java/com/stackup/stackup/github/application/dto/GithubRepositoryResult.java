package com.stackup.stackup.github.application.dto;

import com.stackup.stackup.github.domain.GithubRepository;
import com.stackup.stackup.github.domain.RepositoryStatus;
import java.time.Instant;

public record GithubRepositoryResult(
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
    public static GithubRepositoryResult of(GithubRepository repo) {
        return new GithubRepositoryResult(
            repo.getId(),
            repo.getGithubRepoId(),
            repo.getRepoName(),
            repo.getRepoFullName(),
            repo.getRepoUrl(),
            repo.getDefaultBranch(),
            repo.getStatus(),
            repo.getLastSyncedAt(),
            repo.getCreatedAt(),
            repo.getUpdatedAt()
        );
    }
}
