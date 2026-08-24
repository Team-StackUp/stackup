-- #219 는 "앞으로의 삭제"만 다뤘다. 그 전에 지운 자료의 내용물은 그대로 남아 있다 —
-- S3 의 원본 PDF·분석 마크다운과 document_embeddings 의 청크 원문.
-- V31(탈퇴자 GitHub 토큰)과 같은 종류의 빠진 백필이다.
--
-- 임베딩은 DB 안에서 끝나므로 여기서 정리한다. S3 객체는 애플리케이션이 지워야 해서
-- OrphanedObjectSweeper 가 맡는다 — 그쪽은 #219 의 AFTER_COMMIT 파기가 실패했을 때
-- (설계상 로그만 남기고 넘어간다) 남는 객체까지 같은 경로로 회수한다.
DELETE FROM document_embeddings
WHERE document_id IN (SELECT id FROM analyzed_documents WHERE is_deleted = TRUE);

-- 스위퍼가 S3 객체를 지운 뒤 file_path 를 NULL 로 비워 "회수 완료"를 표시하려면
-- 타입별 locator CHECK 이 삭제된 행을 예외로 둬야 한다. users 의 provider 식별자
-- CHECK(V28)·유니크 인덱스(V3·V22)가 이미 쓰는 규약과 같다 — 제약의 의도는
-- "살아있는 행은 갖춰야 한다" 이지 "지운 행도 영원히 들고 있어라" 가 아니다.
ALTER TABLE resumes DROP CONSTRAINT IF EXISTS chk_resumes_locator_by_type;
ALTER TABLE resumes ADD CONSTRAINT chk_resumes_locator_by_type
    CHECK (
        is_deleted = TRUE
        OR (file_type = 'PDF' AND file_path IS NOT NULL)
        OR (file_type = 'WEB' AND source_url IS NOT NULL)
    );
