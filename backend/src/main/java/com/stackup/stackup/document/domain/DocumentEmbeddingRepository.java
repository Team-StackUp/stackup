package com.stackup.stackup.document.domain;

import java.util.List;

public interface DocumentEmbeddingRepository {

    int upsertAll(long documentId, String model, List<EmbeddingChunk> chunks);

    int countByDocumentId(long documentId);

    // 자료 삭제 시 청크 원문을 즉시 파기한다. 청크에는 이력서 본문이 그대로 들어 있어
    // 행이 남아 있는 한 "지웠다"고 할 수 없다(검색에서 빼는 것과는 다른 문제다).
    int deleteByDocumentIds(List<Long> documentIds);

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
