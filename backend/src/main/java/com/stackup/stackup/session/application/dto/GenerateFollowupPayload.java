package com.stackup.stackup.session.application.dto;

import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import java.util.List;

public record GenerateFollowupPayload(
    Long sessionId,
    Long parentMessageId,
    Long answerMessageId,
    String previousQuestion,
    String answerText,
    SessionMode mode,
    JobCategory jobCategory,
    List<Long> contextDocumentIds,  // RAG(자료 근거/correctness) 용
    String parentCategory,          // 직전 질문 카테고리 (루브릭 선택)
    String parentExpectedSignal,    // 직전 질문이 기대하는 핵심(평가 관점) — 충족도 채점용
    Long followupMessageId,         // 스트리밍 표시용 placeholder 메시지 ID (AI 콜백에서 채움)
    List<HistoryItem> history       // 최근 대화 (중복 회피)
) {
    public record HistoryItem(String role, String content) {
    }
}
