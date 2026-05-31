package com.stackup.stackup.document.domain;

import java.util.List;

public interface DocumentEmbeddingRepository {

    int upsertAll(long documentId, String model, List<EmbeddingChunk> chunks);

    int countByDocumentId(long documentId);

    // pgvector cosine distance topK 검색. documentIds 가 비어 있으면 전체 대상.
    List<SearchHit> search(float[] queryEmbedding, List<Long> documentIds, int topK);

    record EmbeddingChunk(int chunkIndex, String chunkText, float[] embedding) {
    }

    record SearchHit(long documentId, int chunkIndex, String chunkText, double distance) {
    }
}
