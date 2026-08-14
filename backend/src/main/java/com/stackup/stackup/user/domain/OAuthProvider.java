package com.stackup.stackup.user.domain;

/**
 * 계정이 어떤 OAuth provider 로 만들어졌는지.
 *
 * auth 가 아니라 user 패키지에 있는 이유 — provider 는 users 테이블에 저장되는 계정의 속성이라
 * User 엔티티가 참조해야 하는데, auth 는 이미 user 를 의존한다. auth.domain 에 두면
 * user → auth → user 순환이 생겨 ArchUnit 이 막는다.
 */
public enum OAuthProvider {
    GITHUB,
    GOOGLE
}
