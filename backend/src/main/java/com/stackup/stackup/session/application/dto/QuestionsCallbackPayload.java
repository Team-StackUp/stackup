package com.stackup.stackup.session.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuestionsCallbackPayload(
    Long sessionId,
    String kind,                       // POOL | FOLLOWUP
    List<GeneratedQuestion> questions, // POOL 시 사용
    Long parentMessageId,              // FOLLOWUP 시 사용
    String followupQuestion,           // FOLLOWUP 시 사용
    AnswerEvaluation answerEvaluation  // FOLLOWUP 시 옵션
) {
    public boolean isPool() {
        return "POOL".equals(kind);
    }

    public boolean isFollowup() {
        return "FOLLOWUP".equals(kind);
    }

    public record GeneratedQuestion(
        String category,
        String question
    ) {
    }

    public record AnswerEvaluation(
        Double specificity,
        Double logic,
        String structure
    ) {
    }
}
