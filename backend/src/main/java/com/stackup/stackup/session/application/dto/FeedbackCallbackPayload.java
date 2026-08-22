package com.stackup.stackup.session.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// callback.feedback (AI → Core). 점수는 0~100, NULL 허용 (LLM 산정 불가 시).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedbackCallbackPayload(
    Long sessionId,
    Double overallScore,
    Double technicalAccuracy,
    Double logicScore,
    Double communicationScore,
    String strengthsSummary,
    String weaknessesSummary,
    List<String> improvementKeywords,
    List<String> studyPlan,
    // 강조 표시용 핵심 구절(강점·개선 본문 발췌). 프론트가 부분 문자열 매칭해 하이라이트.
    List<String> highlights,
    List<PanelBreakdownItem> panelBreakdown,
    // 질문별 복기 (답변 메시지별 모범 답안·리라이트·코칭). 비면 복기 없음.
    List<AnswerCoachingItem> answerCoaching,
    String reportS3Key,
    String status,                     // OK(기본) | FAILED. null 이면 구버전 취급(OK).
    String errorCode,
    String errorMessage,
    Boolean retriable
) {
    // 구버전(실패 신호 없던 시절) 호출부·테스트 호환용 — status=OK 로 위임.
    public FeedbackCallbackPayload(
        Long sessionId, Double overallScore, Double technicalAccuracy, Double logicScore,
        Double communicationScore, String strengthsSummary, String weaknessesSummary,
        List<String> improvementKeywords, List<String> studyPlan, List<String> highlights,
        List<PanelBreakdownItem> panelBreakdown, List<AnswerCoachingItem> answerCoaching,
        String reportS3Key
    ) {
        this(sessionId, overallScore, technicalAccuracy, logicScore, communicationScore,
            strengthsSummary, weaknessesSummary, improvementKeywords, studyPlan, highlights,
            panelBreakdown, answerCoaching, reportS3Key, "OK", null, null, null);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }
}
