package com.stackup.stackup.session.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuestionsCallbackPayload(
    Long sessionId,
    String kind,                       // POOL | FOLLOWUP
    List<GeneratedQuestion> questions, // POOL legacy kind: initial question result
    Long parentMessageId,              // FOLLOWUP 시 사용 (직전 질문)
    Long answerMessageId,              // FOLLOWUP 시 평가 대상 답변 메시지
    String followupQuestion,           // FOLLOWUP 시 사용
    AnswerEvaluation answerEvaluation, // FOLLOWUP 시 옵션
    String answerIntent,               // NORMAL | DONT_KNOW | CLARIFICATION (FOLLOWUP)
    Long followupMessageId,            // 스트리밍 placeholder 메시지 ID (AI→Core 콜백, null 가능)
    String status,                     // OK(기본) | FAILED. null 이면 구버전 취급(OK).
    String errorCode,
    String errorMessage,
    Boolean retriable
) {
    // 구버전(실패 신호 없던 시절) 호출부·테스트 호환용 — status=OK 로 위임.
    public QuestionsCallbackPayload(
        Long sessionId, String kind, List<GeneratedQuestion> questions,
        Long parentMessageId, Long answerMessageId, String followupQuestion,
        AnswerEvaluation answerEvaluation, String answerIntent, Long followupMessageId
    ) {
        this(sessionId, kind, questions, parentMessageId, answerMessageId, followupQuestion,
            answerEvaluation, answerIntent, followupMessageId, "OK", null, null, null);
    }

    public boolean isPool() {
        return "POOL".equals(kind);
    }

    public boolean isFollowup() {
        return "FOLLOWUP".equals(kind);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    public record GeneratedQuestion(
        String category,
        String question,
        String jobCategory,      // 이 질문이 겨냥한 직군(다직군 가중용). null=대표 직군 폴백.
        String targetEvidence,   // 질문 근거(자료 인용). 라이브 노출.
        String expectedSignal    // 좋은 답이 드러낼 것. 내부 저장만(라이브 비노출).
    ) {
    }

    public record AnswerEvaluation(
        Double specificity,
        Double logic,
        String structure,
        Double correctness   // 자료 근거 사실성(0~5). RAG 컨텍스트 없으면 null.
    ) {
    }
}
