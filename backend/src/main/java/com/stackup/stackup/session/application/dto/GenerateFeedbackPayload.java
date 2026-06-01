package com.stackup.stackup.session.application.dto;

import java.util.List;

// generate.feedback envelope payload (Core → AI).
// AI 가 세션 메시지/평가/분석문서 컨텍스트 기반으로 종합 피드백을 생성.
public record GenerateFeedbackPayload(
    Long sessionId,
    String mode,
    String jobCategory,
    Integer totalQuestionCount,
    String endReason,                              // USER_REQUEST | MAX_QUESTIONS_REACHED
    List<MessageItem> messages,
    List<Long> contextDocumentIds                  // pgvector RAG 검색용 — AI 가 search API 호출 시 사용
) {

    public record MessageItem(
        Long id,
        Integer sequenceNumber,
        String role,                               // INTERVIEWER | INTERVIEWEE
        String content,
        Long parentMessageId
    ) {
    }
}
