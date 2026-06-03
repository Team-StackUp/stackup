-- 피드백 공개 공유 토큰. null = 비공개. "공유" 시 UUID 발급.
ALTER TABLE session_feedbacks
    ADD COLUMN share_token VARCHAR(64) UNIQUE;
