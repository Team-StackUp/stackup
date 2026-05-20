package com.stackup.stackup.document.domain;

import java.util.List;

public interface DocumentEmbeddingRepository {

    int upsertAll(long documentId, String model, List<EmbeddingChunk> chunks);

    int countByDocumentId(long documentId);

    record EmbeddingChunk(int chunkIndex, String chunkText, float[] embedding) {
    }
}
