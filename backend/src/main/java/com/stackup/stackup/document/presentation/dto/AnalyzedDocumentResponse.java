package com.stackup.stackup.document.presentation.dto;

import com.stackup.stackup.document.application.dto.AnalyzedDocumentResult;
import com.stackup.stackup.document.domain.AnalysisStatus;
import com.stackup.stackup.document.domain.DocumentStatus;
import java.net.URI;
import java.time.Instant;
import java.util.List;

public record AnalyzedDocumentResponse(
    Long id,
    String sourceType,
    Long sourceId,
    String documentPath,
    URI documentDownloadUrl,
    String summary,
    List<String> techStack,
    int embeddingChunkCount,
    AnalysisStatus analysisStatus,
    String errorCode,
    String errorMessage,
    DocumentStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static AnalyzedDocumentResponse from(AnalyzedDocumentResult r) {
        return new AnalyzedDocumentResponse(
            r.id(),
            r.sourceType(),
            r.sourceId(),
            r.documentPath(),
            r.documentDownloadUrl(),
            r.summary(),
            r.techStack(),
            r.embeddingChunkCount(),
            r.analysisStatus(),
            r.errorCode(),
            r.errorMessage(),
            r.status(),
            r.createdAt(),
            r.updatedAt()
        );
    }
}
