-- 피드백 생성 실패의 영속 마커. callback.feedback FAILED 는 지금까지 SSE ERROR 로만 알려져
-- 그 순간 미접속(새로고침·탭 닫힘)이던 클라이언트는 실패를 알 길이 없었다 — GET 피드백이
-- "아직 없음"(FEEDBACK_NOT_READY)만 돌려줘 "생성 중"과 구분 불가, 폴링 예산(≈2분)까지 헛대기.
-- FAILED 수신 시 기록하고 성공 콜백 도착·재생성 요청 시 클리어해, REST 경로에서도
-- FEEDBACK_GENERATION_FAILED 로 즉시 구분할 수 있게 한다.
ALTER TABLE interview_sessions ADD COLUMN feedback_failed_at TIMESTAMPTZ;
ALTER TABLE interview_sessions ADD COLUMN feedback_fail_retriable BOOLEAN;
