package com.stackup.stackup.common.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 스위퍼들은 할 일이 없으면 로그를 남기지 않는다 — 그래서 "돌았는데 대상이 없었다"와
 * "아예 안 돌았다"를 밖에서 구분할 수 없었다. 그 구분을 만드는 게 심장박동이다.
 */
class SchedulerHealthIndicatorTest {

    @SuppressWarnings("unchecked")
    private SchedulerHeartbeat heartbeatAt(Instant at) {
        SchedulerHeartbeat hb = new SchedulerHeartbeat();
        ((AtomicReference<Instant>) ReflectionTestUtils.getField(hb, "lastBeat")).set(at);
        return hb;
    }

    @Test
    void 최근_박동이_있으면_UP() {
        Health health = new SchedulerHealthIndicator(
            heartbeatAt(Instant.now().minus(30, ChronoUnit.SECONDS)), 300).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("ageSeconds", 30L);
    }

    @Test
    void 한도를_넘긴_박동은_DOWN() {
        // 스케줄러가 붙잡혀 있다 — 멈춘 면접을 푸는 스위퍼들도 같이 멈춰 있다는 뜻.
        Health health = new SchedulerHealthIndicator(
            heartbeatAt(Instant.now().minus(10, ChronoUnit.MINUTES)), 300).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void 경계값_직전은_UP() {
        Health health = new SchedulerHealthIndicator(
            heartbeatAt(Instant.now().minus(299, ChronoUnit.SECONDS)), 300).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    // 이 두 테스트는 예전에 정반대를 고정하고 있었다("한 번도 안 뛰었으면 DOWN").
    // 그게 곧 버그였다 — 부팅 직후 10초(첫 박동 전)를 "스케줄러가 죽었다"로 보고해
    // 2026-09-29 배포에서 팀 채널에 헛알림이 나갔다.
    @Test
    void 기동_직후_첫_박동_전에도_UP() {
        Health health = new SchedulerHealthIndicator(new SchedulerHeartbeat(), 300).health();

        assertThat(health.getStatus())
            .as("방금 부팅한 것과 스케줄러가 죽은 것은 다르다")
            .isEqualTo(Status.UP);
    }

    @Test
    void 한_번도_안_뛴_채_한도를_넘기면_DOWN() {
        // 기동 시각으로 시드해도 스케줄러가 영영 안 뛰면 그 값이 늙어 잡힌다.
        // 감지가 최대 maxAge 만큼 늦어질 뿐이고, 그게 "안 뛴다"의 정의다.
        SchedulerHeartbeat neverBeat = heartbeatAt(Instant.now().minus(6, ChronoUnit.MINUTES));

        assertThat(new SchedulerHealthIndicator(neverBeat, 300).health().getStatus())
            .isEqualTo(Status.DOWN);
    }

    @Test
    void beat_는_시각을_갱신한다() {
        SchedulerHeartbeat hb = new SchedulerHeartbeat();
        Instant seeded = hb.lastBeat();
        assertThat(seeded).as("기동 시각으로 시드된다").isNotNull();

        hb.beat();

        assertThat(hb.lastBeat()).isAfterOrEqualTo(seeded);
    }
}
