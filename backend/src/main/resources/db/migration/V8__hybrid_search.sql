-- =============================================================================
-- 하이브리드 검색(벡터 + BM25 full-text + RRF) 지원
-- =============================================================================
-- 기술 용어(gRPC, Kafka 등)의 정확 매칭을 위해 full-text 검색을 병행한다.
-- 의미 유사(벡터)만으로는 "단어가 들어있는 청크"를 놓칠 수 있으므로,
-- tsvector 기반 키워드 검색 결과와 RRF(Reciprocal Rank Fusion)로 융합한다.

-- chunk_text 의 full-text 색인용 generated 컬럼.
-- 'simple' config: 스테밍/불용어 없이 토큰화+소문자화만 → 영문 기술용어/한글 혼용에 적합.
-- GENERATED ALWAYS STORED 라 chunk_text 변경 시 자동 동기화 (upsert 코드 변경 불필요).
ALTER TABLE document_embeddings
    ADD COLUMN chunk_text_tsv tsvector
        GENERATED ALWAYS AS (to_tsvector('simple', chunk_text)) STORED;

CREATE INDEX idx_document_embeddings_tsv
    ON document_embeddings USING GIN (chunk_text_tsv);

-- =============================================================================
-- ANN 인덱스 ivfflat → HNSW 교체
-- =============================================================================
-- ivfflat 은 k-means 학습 기반이라 빈 테이블 생성 시 centroid 가 부정확하고
-- 쿼리 시 probes 튜닝이 필요하다. HNSW 는 학습 불필요 + recall/latency 우수.
DROP INDEX IF EXISTS idx_document_embeddings_ann;

CREATE INDEX idx_document_embeddings_hnsw
    ON document_embeddings USING hnsw (embedding vector_cosine_ops);
