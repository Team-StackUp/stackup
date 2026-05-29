package com.stackup.stackup.auth.application;

import com.stackup.stackup.auth.domain.RefreshTokenRepository;
import com.stackup.stackup.user.application.event.UserDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// user 도메인 UserDeletedEvent 수신 → 해당 사용자의 모든 refresh token revoke.
// user → auth 직접 의존을 피하기 위한 분리. auth → user 단방향만 유지.
@Component
@RequiredArgsConstructor
public class UserDeletionRevokeListener {

    private static final Logger log = LoggerFactory.getLogger(UserDeletionRevokeListener.class);

    private final RefreshTokenRepository refreshTokenRepository;

    @EventListener
    @Transactional
    public void on(UserDeletedEvent event) {
        int revoked = refreshTokenRepository.revokeAllByUserId(event.userId());
        log.info("refresh tokens revoked on user delete. userId={}, revoked={}", event.userId(), revoked);
    }
}
