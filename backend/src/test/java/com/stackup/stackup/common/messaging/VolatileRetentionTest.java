package com.stackup.stackup.common.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.auth.domain.RefreshToken;
import com.stackup.stackup.auth.domain.RefreshTokenRepository;
import com.stackup.stackup.common.messaging.domain.ProcessedMessage;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.log.ai.domain.AiRequestLog;
import com.stackup.stackup.log.ai.domain.AiRequestLogRepository;
import com.stackup.stackup.log.ai.domain.AiRequestStatus;
import com.stackup.stackup.support.PostgresRepositoryTest;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 휘발성 레코드의 보존 정리. 지우는 쪽이 없어 사실상 영구 보관이던 두 테이블이다.
 *
 * <p>멱등 레코드는 <b>너무 일찍 지우면 안 된다</b> — DLQ 에서 늦게 재주입된 메시지가
 * "처음 보는 메시지"가 되어 다시 처리되면 질문이 두 번 붙거나 피드백이 재생성된다.
 * 그래서 "기한이 지난 것만" 지우는지를 양쪽으로 확인한다.
 */
@PostgresRepositoryTest
class VolatileRetentionTest {

    @Autowired ProcessedMessageRepository processedMessageRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired AiRequestLogRepository aiRequestLogRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager em;

    @Test
    void deletesOnlyProcessedMessagesOlderThanThreshold() {
        Instant now = Instant.now();
        ProcessedMessage old = processedMessage("m-old", now.minus(Duration.ofDays(40)));
        ProcessedMessage recent = processedMessage("m-recent", now.minus(Duration.ofDays(1)));
        processedMessageRepository.save(old);
        processedMessageRepository.save(recent);
        em.flush();

        int deleted = processedMessageRepository.deleteProcessedBefore(now.minus(Duration.ofDays(30)));

        assertThat(deleted).isEqualTo(1);
        assertThat(processedMessageRepository.existsById("m-old")).isFalse();
        // 최근 것은 남아야 한다 — 지우면 재전달된 메시지가 중복 처리된다.
        assertThat(processedMessageRepository.existsById("m-recent")).isTrue();
    }

    @Test
    void deletesOnlyExpiredRefreshTokens() {
        User user = userRepository.save(User.createGithubUser(95001L, "token-user", null, null, "t"));
        Instant now = Instant.now();
        RefreshToken expired = refreshTokenRepository.save(
            RefreshToken.issue(user, "hash-expired", null, now.minus(Duration.ofDays(1))));
        RefreshToken live = refreshTokenRepository.save(
            RefreshToken.issue(user, "hash-live", null, now.plus(Duration.ofDays(7))));
        em.flush();

        int deleted = refreshTokenRepository.deleteExpiredBefore(now);

        assertThat(deleted).isEqualTo(1);
        assertThat(refreshTokenRepository.findById(expired.getId())).isEmpty();
        // 아직 유효한 토큰은 건드리지 않는다 — 지우면 로그인 세션이 끊긴다.
        assertThat(refreshTokenRepository.findById(live.getId())).isPresent();
    }

    // LLM 호출마다 한 행이라 가장 빨리 자라는 테이블. 보존 기한이 지난 것만 지운다.
    @Test
    void deletesOnlyAiRequestLogsOlderThanThreshold() {
        Instant now = Instant.now();
        AiRequestLog old = aiRequestLogRepository.save(aiLog("generate.questions"));
        AiRequestLog recent = aiRequestLogRepository.save(aiLog("generate.followup"));
        ReflectionTestUtils.setField(old, "createdAt", now.minus(Duration.ofDays(120)));
        ReflectionTestUtils.setField(recent, "createdAt", now.minus(Duration.ofDays(10)));
        em.flush();

        int deleted = aiRequestLogRepository.deleteCreatedBefore(now.minus(Duration.ofDays(90)));

        assertThat(deleted).isEqualTo(1);
        assertThat(aiRequestLogRepository.findById(old.getId())).isEmpty();
        // 보존 기간 안의 로그는 남아야 한다 — 비용 추이를 보는 근거다.
        assertThat(aiRequestLogRepository.findById(recent.getId())).isPresent();
    }

    private AiRequestLog aiLog(String requestType) {
        return AiRequestLog.of(null, null, requestType, "gemini-3.1-flash",
            100, 200, 1200, AiRequestStatus.SUCCESS, null);
    }

    private ProcessedMessage processedMessage(String id, Instant processedAt) {
        ProcessedMessage pm = ProcessedMessage.of(id, "test-consumer");
        ReflectionTestUtils.setField(pm, "processedAt", processedAt);
        return pm;
    }
}
