package com.stackup.stackup.common.health;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 스케줄러 생존 여부.
 *
 * <p>{@link SchedulerHeartbeat} 의 마지막 박동이 한도를 넘으면 DOWN. 스케줄러가 멈췄다는
 * 것은 <b>멈춘 면접을 푸는 스위퍼들도 같이 멈췄다</b>는 뜻이다 — 지금까지는 이 상태를
 * 밖에서 알 방법이 아예 없었다.
 *
 * <p>{@code BackupHealthIndicator} 와 같은 이유로 <b>aggregate 에는 넣지 않는다</b>
 * (informational). 스케줄러가 멈춰도 면접 진행·로그인·조회는 정상 동작한다. 여기서 DOWN 은
 * "서비스가 죽었다"가 아니라 "고장 복구가 안 되고 있다"는 뜻이고, 그 둘을 섞으면
 * 업타임 감시가 헛울린다.
 *
 * <p>빈 이름이 곧 Actuator 컴포넌트 키가 된다 — {@code schedulerHealthIndicator} → {@code "scheduler"}.
 */
@Component
public class SchedulerHealthIndicator implements HealthIndicator {

    private final SchedulerHeartbeat heartbeat;
    private final Duration maxAge;

    public SchedulerHealthIndicator(
        SchedulerHeartbeat heartbeat,
        // 박동 주기(기본 60초)의 5배. 한두 번 밀리는 것으로 울리면 아무도 안 본다.
        @Value("${scheduling.heartbeat-max-age-seconds:300}") long maxAgeSeconds
    ) {
        this.heartbeat = heartbeat;
        this.maxAge = Duration.ofSeconds(maxAgeSeconds);
    }

    @Override
    public Health health() {
        Instant last = heartbeat.lastBeat();
        if (last == null) {
            // 기동 직후(initialDelay 이내)에도 여기로 온다. 그래서 aggregate 에서 빼 둔다 —
            // 재기동마다 전체 상태가 잠깐 DOWN 으로 보이면 안 된다.
            return Health.down()
                .withDetail("reason", "스케줄러가 아직 한 번도 실행되지 않았습니다")
                .build();
        }
        Duration age = Duration.between(last, Instant.now());
        Health.Builder builder = age.compareTo(maxAge) > 0 ? Health.down() : Health.up();
        return builder
            .withDetail("lastBeat", last.toString())
            .withDetail("ageSeconds", age.toSeconds())
            .build();
    }
}
