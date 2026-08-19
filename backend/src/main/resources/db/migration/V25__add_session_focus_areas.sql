-- B-3 약점 집중 재도전. 이전 면접 피드백에서 낮았던 평가 축을 새 세션에 새겨,
-- generate.questions 가 그 영역을 검증하는 질문을 우선 배치하게 한다.
-- 값 예: ["LOGIC","COMMUNICATION"] (SessionFocusArea enum name 배열). 지정 없으면 NULL.
ALTER TABLE interview_sessions ADD COLUMN focus_areas JSONB;
