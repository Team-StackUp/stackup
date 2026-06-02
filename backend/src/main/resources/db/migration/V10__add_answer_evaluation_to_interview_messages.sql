-- =============================================================================
-- 답변 평가 영속 (꼬리질문 단계 채점값) — 피드백에서 롤업 재활용
-- =============================================================================
-- 답변(INTERVIEWEE) 메시지에만 채워진다. 모두 nullable.
-- specificity/logic/correctness: 0~5, structure: STAR enum 문자열.
ALTER TABLE interview_messages
    ADD COLUMN answer_specificity DOUBLE PRECISION,
    ADD COLUMN answer_logic DOUBLE PRECISION,
    ADD COLUMN answer_structure VARCHAR(20),
    ADD COLUMN answer_correctness DOUBLE PRECISION;
