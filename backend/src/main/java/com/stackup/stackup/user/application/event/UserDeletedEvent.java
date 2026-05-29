package com.stackup.stackup.user.application.event;

// 회원 탈퇴 시 발행. auth 슬라이스 listener 가 받아 refresh token revoke.
// user → auth 직접 의존을 피하기 위한 매개체. auth → user 단방향만 유지.
public record UserDeletedEvent(
    Long userId
) {
}
