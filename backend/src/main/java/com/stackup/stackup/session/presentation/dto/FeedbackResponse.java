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
    @Schema(description = "Active share token (owner endpoint only; null = not shared). Absent on the public endpoint.")
    String shareToken,
    Instant createdAt
) {

    public static FeedbackResponse from(FeedbackResult r) {
        return build(r, r.reportFilePath(), r.shareToken());
    }

    // 공개(비인증) 응답: 호출자가 이미 토큰을 알고 있더라도 응답 본문에는 싣지 않는다 —
    // 캐시·로그·스크린샷 경유 재유출 면을 줄인다. reportFilePath(내부 S3 키)도 같은 이유로
    // 제외 — 리포트 프록시는 소유자 전용이라 공개 화면에선 쓸 수도 없다.
    public static FeedbackResponse fromPublic(FeedbackResult r) {
        return build(r, null, null);
    }

    private static FeedbackResponse build(FeedbackResult r, String reportFilePath, String shareToken) {
        return new FeedbackResponse(
            r.id(), r.sessionId(),
            r.overallScore(), r.technicalAccuracy(), r.logicScore(), r.communicationScore(),
            r.strengthsSummary(), r.weaknessesSummary(),
            r.improvementKeywords(), r.panelBreakdown(), r.studyPlan(), r.highlights(),
            reportFilePath, shareToken, r.createdAt()
        );
    }
}
