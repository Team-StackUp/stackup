package com.stackup.stackup.auth.application;

import com.stackup.stackup.auth.application.dto.AuthenticatedUserResult;
import com.stackup.stackup.user.domain.User;

// User → AuthenticatedUserResult 매핑을 provider 별 서비스가 각자 복제하지 않게 한 곳에 둔다.
final class AuthUserResults {

    private AuthUserResults() {
    }

    static AuthenticatedUserResult from(User user) {
        return new AuthenticatedUserResult(
            user.getId(),
            user.getProvider(),
            user.getDisplayName(),
            user.getGithubId(),
            user.getGithubUsername(),
            user.getEmail(),
            user.getAvatarUrl()
        );
    }
}
