-- 피드백 핵심 구절 하이라이트: 강점·개선 본문에서 발췌한 짧은 구절 배열.
-- 프론트가 부분 문자열 매칭으로 리포트에서 <mark> 강조.
ALTER TABLE session_feedbacks
    ADD COLUMN highlights JSONB NOT NULL DEFAULT '[]'::jsonb;
