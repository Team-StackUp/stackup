package com.stackup.stackup.session.application.dto;

// analyze.voice envelope payload (Core → AI). AI 가 S3 GET → STT → 분석 후 callback.voice 발행.
public record AnalyzeVoicePayload(
    Long sessionId,
    Long messageId,
    Long parentQuestionMessageId,           // 직전 INTERVIEWER 메시지 (꼬리질문 트리거용 reference)
    String audioS3Key,
    String contentType,
    String previousQuestionText,            // STT 정확도 향상용 hint (optional)
    String interviewType,
    String jobCategory
) {
}
