package com.stackup.stackup.document.domain;

import java.util.List;

public interface DocumentEmbeddingRepository {

    int upsertAll(long documentId, String model, List<EmbeddingChunk> chunks);

    int countByDocumentId(long documentId);

    // 임베딩 검색. queryText 가 주어지면 벡터 + full-text(BM25) 를 RRF 로 융합한
    // 하이브리드 검색, 없으면(null/blank) pgvector cosine 단독 검색.
    // documentIds 가 비어 있으면 전체 대상.
    List<SearchHit> search(
        float[] queryEmbedding, String queryText, List<Long> documentIds, int topK);

    record EmbeddingChunk(int chunkIndex, String chunkText, float[] embedding) {
    }

    record SearchHit(long documentId, int chunkIndex, String chunkText, double distance) {
    }
}
