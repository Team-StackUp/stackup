-- 멀티 면접관 패널(다직군 가중): 풀의 각 일반질문이 겨냥한 직군 태그.
-- 피드백 시 사용된 질문을 직군별로 집계해 평가위원 가중에 쓴다. null = 대표 직군 폴백.
ALTER TABLE session_question_pool ADD COLUMN job_category varchar(30);
