-- pgvector 확장은 infra/postgres/init.sql 에서 보장 (Docker 이미지 init)
-- 미보장 환경에서도 안전하도록 IF NOT EXISTS
CREATE EXTENSION IF NOT EXISTS vector;

-- =============================================================================
-- analyzed_documents: 분석 라이프사이클 컬럼 추가
-- =============================================================================
-- document_path 는 publish 시점에 비어있고 callback ANALYZED 시 채워짐
ALTER TABLE analyzed_documents
    ALTER COLUMN document_path DROP NOT NULL;

ALTER TABLE analyzed_documents
    ADD COLUMN analysis_status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    ADD COLUMN error_code VARCHAR(50),
    ADD COLUMN error_message VARCHAR(1000),
    ADD COLUMN embedding_chunk_count INT NOT NULL DEFAULT 0;

ALTER TABLE analyzed_documents
    ADD CONSTRAINT chk_analyzed_documents_analysis_status
        CHECK (analysis_status IN ('PROCESSING', 'ANALYZED', 'FAILED'));

CREATE INDEX idx_analyzed_documents_analysis_status
    ON analyzed_documents (analysis_status);

-- =============================================================================
-- document_embeddings: 청크 + pgvector 임베딩
-- =============================================================================
CREATE TABLE document_embeddings (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES analyzed_documents (id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    chunk_text TEXT NOT NULL,
    embedding vector(1536) NOT NULL,
    model VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_document_embeddings_document_chunk
        UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_document_embeddings_document_id
    ON document_embeddings (document_id);

-- ANN 인덱스 (ivfflat). lists 는 데이터 양에 따라 튜닝.
-- 초기에는 lists=100 (수만 row 가정). 추후 ANALYZE 후 재생성.
CREATE INDEX idx_document_embeddings_ann
    ON document_embeddings USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- =============================================================================
-- processed_messages: 비동기 consumer 멱등성 (RabbitMQ messageId 기록)
-- =============================================================================
CREATE TABLE processed_messages (
    message_id VARCHAR(64) PRIMARY KEY,
    consumer VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_processed_messages_processed_at
    ON processed_messages (processed_at);

-- 24h 이상 된 row 는 cron 으로 정리 (운영 가이드: docs/messaging.md §6)
