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

    @Test
    void 한_번도_안_뛰었으면_DOWN() {
        Health health = new SchedulerHealthIndicator(new SchedulerHeartbeat(), 300).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("reason").toString())
            .contains("아직 한 번도 실행되지 않았습니다");
    }

    @Test
    void beat_는_시각을_갱신한다() {
        SchedulerHeartbeat hb = new SchedulerHeartbeat();
        assertThat(hb.lastBeat()).isNull();

        hb.beat();

        assertThat(hb.lastBeat()).isNotNull();
    }
}
