-- US-19 답변 제출 멱등 처리 (SPRINT2_PLAN decision #4)
-- POST /api/sessions/{id}/messages 에서 SSE 재연결 후 자동 재시도 시 중복 INSERT 차단.
-- session 단위로 unique — 동일 키여도 다른 세션이면 별도 답변.

ALTER TABLE interview_messages
    ADD COLUMN idempotency_key VARCHAR(64);

CREATE UNIQUE INDEX uq_interview_messages_session_idem_key
    ON interview_messages (session_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
