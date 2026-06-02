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
    List<HistoryItem> history       // 최근 대화 (중복 회피)
) {
    public record HistoryItem(String role, String content) {
    }
}
