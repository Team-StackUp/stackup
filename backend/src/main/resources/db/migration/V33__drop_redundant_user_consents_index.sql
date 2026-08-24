-- idx_user_consents_user_type (user_id, consent_type) 가 이미 있어서 user_id 단독 인덱스는
-- 그 leftmost prefix 로 커버된다. 실제 쿼리 두 개(findByUser_IdOrderByIdDesc,
-- findFirstByUser_IdAndConsentType...) 모두 복합 인덱스로 처리된다.
-- 중복 인덱스는 조회에 보탬이 없고 쓰기마다 갱신 비용만 더한다.
DROP INDEX IF EXISTS idx_user_consents_user_id;
