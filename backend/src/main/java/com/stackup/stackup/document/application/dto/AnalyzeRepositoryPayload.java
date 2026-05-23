package com.stackup.stackup.document.application.dto;

public record AnalyzeRepositoryPayload(
    Long repositoryId,
    String repoFullName,
    String defaultBranch,
    Long analyzedDocumentId
) {
}
