package com.stackup.stackup.auth.application;

import com.stackup.stackup.auth.domain.RefreshTokenRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료된 refresh token 행을 정리한다.
 *
 * <p>로그인마다 한 행이 쌓이는데 지우는 쪽이 없어 사실상 영구 보관이었다. 만료된 토큰은
 * 검증에서 항상 걸러지므로 남겨둬도 무효다 — 보관해서 얻는 것이 없다.
 *
 * <p>취소(revoked)된 토큰은 만료 전이라면 남긴다. 탈퇴·로그아웃 시 revoke 한 기록이
 * 만료 시각까지는 "이 토큰은 무효"라는 판단 근거로 남아 있어야 한다.
 */
@Component
@RequiredArgsConstructor
public class ExpiredRefreshTokenSweeper {

    private static final Logger log = LoggerFactory.getLogger(ExpiredRefreshTokenSweeper.class);

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    @Scheduled(
        fixedDelayString = "${auth.refresh-token-sweep-interval-ms:86400000}",
        initialDelayString = "${auth.refresh-token-sweep-initial-delay-ms:300000}")
    public void sweep() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(Instant.now());
        if (deleted > 0) {
            log.info("expired refresh tokens swept. deleted={}", deleted);
        }
    }
}
