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
    List<PanelBreakdownItem> panelBreakdown,
    String reportS3Key
) {
}
