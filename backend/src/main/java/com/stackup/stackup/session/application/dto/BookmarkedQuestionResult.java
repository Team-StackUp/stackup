package com.stackup.stackup.session.application.dto;

import java.time.Instant;

// 오답노트 항목 — 표시한 질문 + 그때 내 답변 + 복기 재료.
public record BookmarkedQuestionResult(
    Long messageId,
    Long sessionId,
    String sessionTitle,
    String category,
    String question,
    // 좋은 답변이 드러내야 할 핵심(질문에 기록됨).
    String expectedSignal,
    // 그때 내가 한 답변. 답변 전에 표시했거나 실패한 턴이면 null.
    String myAnswer,
    // 답변에 붙은 복기 재료(피드백 생성 시 기록). 피드백 전이면 null.
    String modelAnswer,
    String coachingComment,
    Instant createdAt
) {
}
