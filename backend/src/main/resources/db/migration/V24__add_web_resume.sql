-- US-09 웹 이력서(URL) — 포트폴리오·블로그·노션 링크를 이력서 자료로 등록한다.
-- docs/messaging.md §5.3 대로 resume 도메인을 재사용하므로(analyze.web 의 payload 가 resumeId)
-- 새 테이블을 만들지 않고 resumes 에 WEB 타입을 추가한다.

-- WEB 은 S3 오브젝트가 없다(원문은 URL). file_path 를 nullable 로 전환.
ALTER TABLE resumes ALTER COLUMN file_path DROP NOT NULL;

ALTER TABLE resumes ADD COLUMN source_url VARCHAR(2000);

ALTER TABLE resumes DROP CONSTRAINT chk_resumes_file_type;
ALTER TABLE resumes ADD CONSTRAINT chk_resumes_file_type
    CHECK (file_type IN ('PDF', 'WEB'));

-- 타입별 필수 컬럼을 DB 에서 강제 — PDF 는 S3 키, WEB 은 URL 이 반드시 있어야 한다.
ALTER TABLE resumes ADD CONSTRAINT chk_resumes_locator_by_type
    CHECK (
        (file_type = 'PDF' AND file_path IS NOT NULL)
        OR (file_type = 'WEB' AND source_url IS NOT NULL)
    );
