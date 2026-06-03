-- 부연(질문 재설명) 메시지 표시. clarification 메시지는 총 질문 수(k)·꼬리 깊이(m)에
-- 카운트되지 않는다. 같은 질문을 다시 제시하는 용도.
ALTER TABLE interview_messages
    ADD COLUMN clarification BOOLEAN NOT NULL DEFAULT FALSE;
