package com.stackup.stackup.github.application.event;

// GithubRepository soft delete commit 후 발화. document 도메인이 수신해 관련 AnalyzedDocument cascade soft delete.
public record RepositoryDeletedEvent(Long userId, Long repositoryId) {
}
