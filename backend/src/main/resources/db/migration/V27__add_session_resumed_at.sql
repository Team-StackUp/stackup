-- B-5 중단 세션 이어하기. INTERRUPTED → IN_PROGRESS 재개를 허용한다.
-- 시간 한도(max_duration_minutes)는 started_at 기준인데, 한참 뒤에 재개하면 스위퍼가
-- 즉시 다시 중단시킨다. 재개 시각을 따로 두고 스위퍼가 COALESCE(resumed_at, started_at)
-- 기준으로 재도록 해, 이어하기마다 그 자리(sitting)의 시간이 새로 시작되게 한다.
-- started_at 은 '처음 시작한 시각'으로 보존된다(히스토리 표시용).
ALTER TABLE interview_sessions ADD COLUMN resumed_at TIMESTAMPTZ;
