-- 자소서 원문(items)은 다른 자료와 달리 S3 가 아니라 행 안에 산다. 삭제 시 분석
-- 마크다운·임베딩은 파기되지만(#219 cascade) 원문은 남았다. 이 릴리스부터 삭제 시
-- 함께 비우는데, 그 전에 지운 자소서는 삭제 이벤트가 다시 나지 않으므로 백필한다
-- (V31 탈퇴자 토큰 · #223 과거 삭제분 S3 객체와 같은 종류 — 새 규칙의 소급 적용).
UPDATE cover_letters
SET items = '[]'::jsonb
WHERE is_deleted = TRUE
  AND items <> '[]'::jsonb;
