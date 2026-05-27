package com.stackup.stackup.document.application.dto;

import com.stackup.stackup.document.domain.AnalysisStatus;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.DocumentStatus;
import java.net.URI;
import java.time.Instant;
import java.util.List;

public record AnalyzedDocumentResult(
    Long id,
    String sourceType,             // "RESUME" | "REPOSITORY"
    Long sourceId,
    String documentPath,           // S3 키 (raw 경로)
    URI documentDownloadUrl,       // presigned (detail 응답에서만 채움)
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
    public static AnalyzedDocumentResult of(AnalyzedDocument doc, List<String> techStack, URI downloadUrl) {
        return new AnalyzedDocumentResult(
            doc.getId(),
            resolveSourceType(doc),
            resolveSourceId(doc),
            doc.getDocumentPath(),
            downloadUrl,
            doc.getSummary(),
            techStack,
            doc.getEmbeddingChunkCount(),
            doc.getAnalysisStatus(),
            doc.getErrorCode(),
            doc.getErrorMessage(),
            doc.getStatus(),
            doc.getCreatedAt(),
            doc.getUpdatedAt()
        );
    }

    private static String resolveSourceType(AnalyzedDocument doc) {
        if (doc.getResume() != null) return "RESUME";
        if (doc.getRepository() != null) return "REPOSITORY";
        return null;
    }

    private static Long resolveSourceId(AnalyzedDocument doc) {
        if (doc.getResume() != null) return doc.getResume().getId();
        if (doc.getRepository() != null) return doc.getRepository().getId();
        return null;
    }
}
