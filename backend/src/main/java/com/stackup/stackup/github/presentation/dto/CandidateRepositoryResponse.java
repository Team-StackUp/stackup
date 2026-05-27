package com.stackup.stackup.github.presentation.dto;

import com.stackup.stackup.github.application.dto.CandidateRepositoryResult;

public record CandidateRepositoryResponse(
    Long githubRepoId,
    String name,
    String fullName,
    String htmlUrl,
    String defaultBranch,
    boolean isPrivate,
    boolean alreadyRegistered
) {
    public static CandidateRepositoryResponse from(CandidateRepositoryResult result) {
        return new CandidateRepositoryResponse(
            result.githubRepoId(),
            result.name(),
            result.fullName(),
            result.htmlUrl(),
            result.defaultBranch(),
            result.isPrivate(),
            result.alreadyRegistered()
        );
    }
}
