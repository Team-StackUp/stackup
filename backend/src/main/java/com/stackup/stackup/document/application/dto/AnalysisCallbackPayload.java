package com.stackup.stackup.document.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisCallbackPayload(
    String targetType,
    Long targetId,
    String status,
    String summary,
    List<String> techStack,
    String documentPath,
    Integer embeddingChunkCount,
    String errorCode,
    String errorMessage,
    Boolean retriable
) {
    public boolean isAnalyzed() {
        return "ANALYZED".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }
}
