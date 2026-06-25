package com.stackup.stackup.session.application.dto;

import java.util.List;
import java.util.Map;

// generate.feedback envelope payload (Core -> AI).
// AI generates comprehensive feedback from session messages, evaluation context, documents, and voice summary.
public record GenerateFeedbackPayload(
    Long sessionId,
    String mode,
    String jobCategory,
    Integer totalQuestionCount,
    String endReason,
    List<MessageItem> messages,
    List<Long> contextDocumentIds,
    VoiceAnalysisSummary voiceAnalysisSummary,
    // 다직군 패널 가중: 사용된 일반질문의 직군별 개수(예: {"BACKEND":3,"FRONTEND":2}). 비면 단일.
    Map<String, Integer> domainQuestionCounts
) {

    public record MessageItem(
        Long id,
        Integer sequenceNumber,
        String role,
        String content,
        Long parentMessageId,
        String expectedSignal,         // INTERVIEWER 질문에만(평가 기준). 답변은 null
        String category,               // 질문 유형(SELF_INTRODUCTION 등). 첫인상 평가에서 자기소개 식별용
        MessageEvaluation evaluation   // INTERVIEWEE 답변에만(없으면 null)
    ) {
    }

    public record MessageEvaluation(
        Double specificity,
        Double logic,
        String structure,
        Double correctness
    ) {
    }

    public record VoiceAnalysisSummary(
        int analyzedMessageCount,
        Double averageSpeakingRateWpm,
        Double totalSilenceDurationSec,
        Map<String, Integer> fillerWordCounts
    ) {
    }
}
