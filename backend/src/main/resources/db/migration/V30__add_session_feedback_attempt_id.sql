-- 피드백 생성 시도 상관관계(F5). V29 실패 마커는 "어느 시도의 실패인지"를 몰라서,
-- 재생성으로 대체된 이전 시도의 지연 FAILED 콜백이 새 시도가 진행 중인 세션에 실패 마커를
-- 되씌울 수 있었다(멱등은 messageId 단위 — 시도 식별자가 payload 에 없음).
-- 발행마다 새 attemptId(UUID)를 발급해 여기에 기록하고 payload 로 왕복시켜,
-- FAILED 콜백의 attemptId 가 현재 값과 다르면 무시한다.
ALTER TABLE interview_sessions ADD COLUMN feedback_attempt_id VARCHAR(36);
