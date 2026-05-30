package com.stackup.stackup.document.application.dto;

import java.util.List;

public record EmbeddingSearchCommand(
        Long sessionId,
        List<Long> documentIds,
        float[] queryEmbedding,
        Integer topK,
        Double minScore
) {
}
