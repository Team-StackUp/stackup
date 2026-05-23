package com.stackup.stackup.document.application.dto;

import java.util.List;

public record EmbeddingUpsertCommand(
    long documentId,
    String model,
    int dim,
    List<Chunk> chunks
) {
    public record Chunk(int chunkIndex, String chunkText, float[] embedding) {
    }
}
