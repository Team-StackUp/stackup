package com.stackup.stackup.github.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stackup.stackup.github.application.dto.CandidateRepositoryResult;

public record CandidateRepositoryResponse(
    Long githubRepoId,
    String name,
    String fullName,
    String htmlUrl,
    String defaultBranch,
    @JsonProperty("private") boolean privateRepo,
    String description,
    boolean alreadyRegistered
) {
    public static CandidateRepositoryResponse from(CandidateRepositoryResult c) {
        return new CandidateRepositoryResponse(
            c.githubRepoId(), c.name(), c.fullName(), c.htmlUrl(), c.defaultBranch(),
            c.privateRepo(), c.description(), c.alreadyRegistered()
        );
    }
}
