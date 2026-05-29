package com.stackup.stackup.session.application.dto;

public record GenerateFollowupPayload(
    Long sessionId,
    Long questionMessageId,
    Long answerMessageId,
    String answerText,
    String audioS3Key // 텍스트 면접에서는 null
) {
}
