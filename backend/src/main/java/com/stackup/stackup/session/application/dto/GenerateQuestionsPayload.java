package com.stackup.stackup.session.application.dto;

import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import java.util.List;

public record GenerateQuestionsPayload(
    Long sessionId,
    SessionMode mode,
    JobCategory jobCategory,
    List<DocumentContext> documents,
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
