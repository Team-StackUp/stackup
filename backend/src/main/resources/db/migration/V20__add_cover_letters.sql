-- 자소서(cover letter): 대기업 공채용 — 문항별(질문+답변) 텍스트 입력.
-- 이력서/레포와 동일하게 분석 → 임베딩 → 세션 컨텍스트 → 질문 생성 파이프라인을 탄다.

CREATE TABLE cover_letters (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    title VARCHAR(200),
    -- 문항 배열: [{"question": "...", "answer": "..."}]
    items JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_cover_letters_status
        CHECK (status IN ('PENDING', 'ANALYZING', 'ANALYZED', 'FAILED'))
);

CREATE INDEX idx_cover_letters_user_id ON cover_letters (user_id);

-- analyzed_documents 다형성 소스에 cover_letter 추가.
ALTER TABLE analyzed_documents
    ADD COLUMN cover_letter_id BIGINT REFERENCES cover_letters (id);

CREATE INDEX idx_analyzed_documents_cover_letter_id
    ON analyzed_documents (cover_letter_id);

-- 기존 "resume XOR repository" 단일 소스 제약을 "셋 중 정확히 하나"로 확장.
ALTER TABLE analyzed_documents
    DROP CONSTRAINT chk_analyzed_documents_single_source;

ALTER TABLE analyzed_documents
    ADD CONSTRAINT chk_analyzed_documents_single_source CHECK (
        (resume_id IS NOT NULL AND repository_id IS NULL AND cover_letter_id IS NULL)
        OR
        (resume_id IS NULL AND repository_id IS NOT NULL AND cover_letter_id IS NULL)
        OR
        (resume_id IS NULL AND repository_id IS NULL AND cover_letter_id IS NOT NULL)
    );
