-- oauth_states.provider 는 V2 에서 CHECK (provider IN ('GITHUB')) 로 고정돼 있었다.
-- V22 로 users 는 GOOGLE 을 받게 했지만 state 테이블을 빠뜨려, Google 로그인을 시작하는
-- 순간(state INSERT) 제약 위반으로 500 이 났다. provider 를 추가할 때 함께 넓혀야 하는 곳.
ALTER TABLE oauth_states DROP CONSTRAINT IF EXISTS chk_oauth_states_provider;

ALTER TABLE oauth_states
    ADD CONSTRAINT chk_oauth_states_provider CHECK (provider IN ('GITHUB', 'GOOGLE'));
