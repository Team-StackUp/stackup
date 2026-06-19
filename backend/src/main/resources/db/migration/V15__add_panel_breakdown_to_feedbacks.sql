-- 멀티 면접관 패널: 평가위원별 분해(평가축/점수/강약점)를 JSON 으로 보관.
-- 비어 있으면 단일/레거시 피드백. 표시 전용이라 jsonb 단일 컬럼으로 충분.
ALTER TABLE session_feedbacks ADD COLUMN panel_breakdown jsonb;
