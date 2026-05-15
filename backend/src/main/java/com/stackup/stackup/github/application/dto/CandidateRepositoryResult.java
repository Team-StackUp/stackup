package com.stackup.stackup.github.application.dto;

public record CandidateRepositoryResult(
    Long githubRepoId,
    String name,
    String fullName,
    String htmlUrl,
    String defaultBranch,
    boolean privateRepo,
    String description,
    boolean alreadyRegistered
) {
}
