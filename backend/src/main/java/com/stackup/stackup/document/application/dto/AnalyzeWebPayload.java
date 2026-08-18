package com.stackup.stackup.document.application.dto;

// analyze.web envelope payload (Core → AI). AI 가 URL 본문을 추출 → 이력서와 동일 분석 체인으로 처리.
// docs/messaging.md §5.3. 콜백은 callback.analysis (targetType=WEB, targetId=resumeId).
public record AnalyzeWebPayload(
    Long resumeId,
    String url,
    Long analyzedDocumentId
) {
}
