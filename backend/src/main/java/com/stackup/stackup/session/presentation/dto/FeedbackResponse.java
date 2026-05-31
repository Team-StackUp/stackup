package com.stackup.stackup.session.presentation.dto;

import com.stackup.stackup.session.application.dto.FeedbackResult;
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
    List<String> improvementKeywords,
    String reportFilePath,
    Instant createdAt
) {

    public static FeedbackResponse from(FeedbackResult r) {
        return new FeedbackResponse(
            r.id(), r.sessionId(),
            r.overallScore(), r.technicalAccuracy(), r.logicScore(), r.communicationScore(),
            r.strengthsSummary(), r.weaknessesSummary(),
            r.improvementKeywords(), r.reportFilePath(), r.createdAt()
        );
    }
}
