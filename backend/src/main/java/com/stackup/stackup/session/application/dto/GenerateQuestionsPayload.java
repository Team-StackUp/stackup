package com.stackup.stackup.session.application.dto;

import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import java.util.List;

public record GenerateQuestionsPayload(
    Long sessionId,
    SessionMode mode,
    JobCategory jobCategory,
    List<DocumentContext> documents,
    // Number of questions AI should generate for this initial callback.
    Integer initialQuestionCount,
    // Overall session question limit; not the generation batch size.
    Integer maxQuestions
) {
    public record DocumentContext(
        Long documentId,
        String sourceType,    // RESUME | REPOSITORY | WEB
        String summary,
        List<String> techStack,
        String markdown
    ) {
    }
}
