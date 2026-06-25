package com.stackup.stackup.session.application.dto;

import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import java.util.List;

public record GenerateQuestionsPayload(
    Long sessionId,
    SessionMode mode,
    List<JobCategory> jobCategories,
    List<DocumentContext> documents,
    // Number of questions AI should generate for this initial callback.
    Integer initialQuestionCount,
    // Overall session question limit; not the generation batch size.
    Integer maxQuestions,
    // 같은 유저가 최근 면접에서 받은 질문들. AI 가 의미 중복 회피에 사용.
    List<String> recentQuestions,
    // 지원자의 자기소개 답변. 질문 생성의 1차 근거(모든 면접의 기본). 없으면 빈 문자열.
    String selfIntroAnswer
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
