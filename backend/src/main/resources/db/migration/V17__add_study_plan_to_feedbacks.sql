-- 상세 피드백: 패널을 통합한 학습 방향/다음 단계 액션 아이템(JSON 문자열 배열). null=레거시.
ALTER TABLE session_feedbacks ADD COLUMN study_plan jsonb;
