-- 탈퇴한 계정은 GitHub access token 을 지운다(비공개 레포까지 읽는 `repo` 스코프 자격증명을
-- 떠난 사용자 몫으로 계속 들고 있지 않기 위해). 그런데 V22 의 CHECK 제약이
-- provider='GITHUB' 인 모든 행에 토큰 NOT NULL 을 요구해서 UPDATE 가 거부된다.
--
-- 제약의 의도는 "살아있는 계정은 provider 에 맞는 식별자를 갖춰야 한다" 이므로,
-- 삭제된 행을 예외로 둔다. users 의 유니크 인덱스들(V3·V22)이 이미
-- `WHERE is_deleted = FALSE` 로 같은 규약을 쓰고 있다.
ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_provider_identity;

ALTER TABLE users
    ADD CONSTRAINT ck_users_provider_identity CHECK (
        is_deleted = TRUE
        OR (provider = 'GITHUB' AND github_id IS NOT NULL AND encrypted_github_access_token IS NOT NULL)
        OR (provider = 'GOOGLE' AND google_id IS NOT NULL)
    );
