-- B-4 오답노트. 다시 볼 질문을 표시해 두고 나중에 모아 복습한다.
-- 질문(INTERVIEWER) 메시지에만 의미가 있다. 세션이 삭제되면 함께 사라진다(질문의 출처가 세션이므로).
ALTER TABLE interview_messages ADD COLUMN bookmarked BOOLEAN NOT NULL DEFAULT FALSE;

-- 북마크 목록 조회는 "내 것 중 표시된 것"이라 부분 인덱스로 충분하다.
CREATE INDEX idx_interview_messages_bookmarked
    ON interview_messages (session_id) WHERE bookmarked = TRUE;
