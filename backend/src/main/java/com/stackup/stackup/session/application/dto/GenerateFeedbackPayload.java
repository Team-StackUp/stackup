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
    VoiceAnalysisSummary voiceAnalysisSummary
) {

    public record MessageItem(
        Long id,
        Integer sequenceNumber,
        String role,
        String content,
        Long parentMessageId
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
