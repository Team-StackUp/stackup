package com.stackup.stackup.document.domain;

import java.util.List;

public interface DocumentEmbeddingRepository {

    int upsertAll(long documentId, String model, List<EmbeddingChunk> chunks);

    int countByDocumentId(long documentId);

    boolean existsActiveSession(long sessionId);

    int countSearchableSessionDocuments(long sessionId, List<Long> documentIds);

    List<EmbeddingSearchRow> search(long sessionId, List<Long> documentIds, float[] queryEmbedding, int topK, Double minScore);

    record EmbeddingChunk(int chunkIndex, String chunkText, float[] embedding) {
    }

    record EmbeddingSearchRow(long documentId, int chunkIndex, String chunkText, double score, String model) {
    }
}
