package com.stackup.stackup.document.application.dto;

public record AnalyzeResumePayload(
    Long resumeId,
    String filePath,
    Long analyzedDocumentId
) {
}
