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
    Integer maxQuestions,
    // 같은 유저가 최근 면접에서 받은 질문들. AI 가 의미 중복 회피에 사용.
    List<String> recentQuestions
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
