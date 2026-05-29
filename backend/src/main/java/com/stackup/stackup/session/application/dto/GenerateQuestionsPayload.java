package com.stackup.stackup.session.application.dto;

import com.stackup.stackup.session.domain.InterviewType;
import com.stackup.stackup.session.domain.JobCategory;
import java.util.List;

public record GenerateQuestionsPayload(
    Long sessionId,
    InterviewType interviewType,
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
