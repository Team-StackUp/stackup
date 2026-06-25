-- 질문별 복기: 답변마다 AI가 생성한 모범 답안 + 내 답변 리라이트 + 한 줄 코칭.
-- 피드백 생성(callback.feedback) 시 답변(INTERVIEWEE) 메시지에 기록되고, 종료 세션 조회에서만 노출된다.

ALTER TABLE interview_messages
    ADD COLUMN model_answer     TEXT,
    ADD COLUMN answer_rewrite   TEXT,
    ADD COLUMN coaching_comment TEXT;
