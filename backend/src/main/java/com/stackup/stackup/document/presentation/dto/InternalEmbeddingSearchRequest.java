package com.stackup.stackup.document.presentation.dto;

import com.stackup.stackup.document.application.dto.EmbeddingSearchCommand;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record InternalEmbeddingSearchRequest(
        @NotNull Long sessionId,
        @NotNull @NotEmpty List<Long> documentIds,
        @NotNull float[] queryEmbedding,
        Integer topK,
        Double minScore
) {

    public EmbeddingSearchCommand toCommand() {
        return new EmbeddingSearchCommand(sessionId, documentIds, queryEmbedding, topK, minScore);
    }
}
