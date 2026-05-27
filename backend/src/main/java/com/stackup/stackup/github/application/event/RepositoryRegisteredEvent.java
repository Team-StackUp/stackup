package com.stackup.stackup.github.application.event;

// 레포 등록 직후 발행. document 도메인이 listener 로 받아 분석 트리거.
public record RepositoryRegisteredEvent(
    Long userId,
    Long repositoryId
) {
}
