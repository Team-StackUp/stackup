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
    public static GithubRepositoryResult from(GithubRepository r) {
        return new GithubRepositoryResult(
            r.getId(), r.getGithubRepoId(), r.getRepoName(), r.getRepoFullName(),
            r.getRepoUrl(), r.getDefaultBranch(), r.getStatus(),
            r.getLastSyncedAt(), r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}
