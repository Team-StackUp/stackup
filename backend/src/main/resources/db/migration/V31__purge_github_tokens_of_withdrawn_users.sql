-- V28 은 탈퇴 시 GitHub 토큰을 비울 수 있도록 CHECK 제약을 완화하기만 했다. 그 시점에
-- **이미 탈퇴해 있던** 사용자들의 토큰은 그대로 남아 있다 — #198 은 이후 탈퇴만 처리한다.
--
-- 그런데 "떠난 사용자의 repo 스코프 자격증명을 무기한 보관하지 않는다"는 목적에서 보면
-- 그 사람들이 바로 그 대상이다. 이미 탈퇴했으니 앞으로 User.withdraw() 가 불릴 일도 없어
-- 백필하지 않으면 영원히 남는다.
UPDATE users
SET encrypted_github_access_token = NULL
WHERE is_deleted = TRUE
  AND encrypted_github_access_token IS NOT NULL;
