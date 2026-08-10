package com.stackup.stackup.session.application.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackup.stackup.session.domain.SessionFeedback;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public record FeedbackResult(
    Long id,
    Long sessionId,
    Double overallScore,
    Double technicalAccuracy,
    Double logicScore,
    Double communicationScore,
    String strengthsSummary,
    String weaknessesSummary,
    List<String> improvementKeywords,
    List<PanelBreakdownItem> panelBreakdown,
    List<String> studyPlan,
    List<String> highlights,
    String reportFilePath,
    // 공유 토큰(null = 비공개). 소유자 응답에만 노출 — 공개 엔드포인트는 응답 변환 시 제거.
    String shareToken,
    Instant createdAt
) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static FeedbackResult of(SessionFeedback f) {
        return new FeedbackResult(
            f.getId(),
            f.getSession().getId(),
            f.getOverallScore(),
            f.getTechnicalAccuracy(),
            f.getLogicScore(),
            f.getCommunicationScore(),
            f.getStrengthsSummary(),
            f.getWeaknessesSummary(),
            parseKeywords(f.getImprovementKeywords()),
            parseBreakdown(f.getPanelBreakdown()),
            parseKeywords(f.getStudyPlan()),
            parseKeywords(f.getHighlights()),
            f.getReportFilePath(),
            f.getShareToken(),
            f.getCreatedAt()
        );
    }

    private static List<PanelBreakdownItem> parseBreakdown(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<PanelBreakdownItem>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseKeywords(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<?> parsed = JSON.readValue(json, List.class);
            return parsed.stream().map(Object::toString).toList();
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
