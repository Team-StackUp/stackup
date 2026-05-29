package com.stackup.stackup.session.application.dto;

import java.util.List;

public record GenerateQuestionsPayload(
    Long sessionId,
    String interviewType,
    String jobCategory,
    List<Long> documentIds,
    int maxQuestions
) {
}
