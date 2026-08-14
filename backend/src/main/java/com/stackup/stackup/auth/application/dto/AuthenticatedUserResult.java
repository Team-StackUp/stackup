package com.stackup.stackup.auth.application.dto;

import com.stackup.stackup.user.domain.OAuthProvider;

/**
 * 로그인한 사용자의 최소 프로필.
 *
 * githubId/githubUsername 은 GitHub 계정에만 있다 — Google 계정에서는 null 이므로 박싱 타입.
 * 화면에 이름을 띄울 때는 provider 와 무관하게 displayName 을 쓴다.
 */
public record AuthenticatedUserResult(
    long id,
    OAuthProvider provider,
    String displayName,
    Long githubId,
    String githubUsername,
    String email,
    String avatarUrl
) {
}
