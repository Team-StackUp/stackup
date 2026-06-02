-- =============================================================================
-- 초기 질문 메타데이터: category / target_evidence / expected_signal
-- =============================================================================
-- 근거 기반 질문을 위해 AI 가 생성한 메타데이터를 보관한다.
-- - category: 질문 유형 (CS_FUNDAMENTAL/PROJECT_DEEP_DIVE/TECH_CHOICE/BEHAVIORAL). 느슨 결합(CHECK 없음).
-- - target_evidence: 질문 근거(자료 인용). 라이브 노출.
-- - expected_signal: 좋은 답이 드러낼 것. 내부용(라이브 비노출).
-- 모두 nullable — 기존 행/꼬리질문은 NULL.
ALTER TABLE interview_messages
    ADD COLUMN category VARCHAR(30),
    ADD COLUMN target_evidence TEXT,
    ADD COLUMN expected_signal TEXT;
