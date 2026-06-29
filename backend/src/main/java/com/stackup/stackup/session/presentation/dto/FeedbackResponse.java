package com.stackup.stackup.session.presentation.dto;

import com.stackup.stackup.session.application.dto.FeedbackResult;
import com.stackup.stackup.session.application.dto.PanelBreakdownItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record FeedbackResponse(
    Long id,
    Long sessionId,
    Double overallScore,
    Double technicalAccuracy,
    Double logicScore,
    Double communicationScore,
    String strengthsSummary,
    String weaknessesSummary,
    @Schema(description = "Improvement keywords returned by AI. The current contract is a string list.")
    List<String> improvementKeywords,
    @Schema(description = "Per-evaluator panel breakdown (multi-interviewer). Empty for single/legacy feedback.")
    List<PanelBreakdownItem> panelBreakdown,
    @Schema(description = "Study plan / next-step action items synthesized from the panel.")
    List<String> studyPlan,
    @Schema(description = "Key phrases (verbatim excerpts from strengths/weaknesses) for the report to highlight.")
    List<String> highlights,
    @Schema(description = "Stored report path when AI generates a detailed learning guide/report.")
    String reportFilePath,
    Instant createdAt
) {

    public static FeedbackResponse from(FeedbackResult r) {
        return new FeedbackResponse(
            r.id(), r.sessionId(),
            r.overallScore(), r.technicalAccuracy(), r.logicScore(), r.communicationScore(),
            r.strengthsSummary(), r.weaknessesSummary(),
            r.improvementKeywords(), r.panelBreakdown(), r.studyPlan(), r.highlights(),
            r.reportFilePath(), r.createdAt()
        );
    }
}
