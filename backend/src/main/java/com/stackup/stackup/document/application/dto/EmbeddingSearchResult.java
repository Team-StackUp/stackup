package com.stackup.stackup.document.application.dto;

public record EmbeddingSearchResult(
        long documentId,
        int chunkIndex,
        String chunkText,
        double score,
        String model
) {
}
