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
    String reportS3Key
) {
}
