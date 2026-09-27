package com.stackup.stackup.common.health;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 백업 신선도.
 *
 * <p>백업 스크립트가 실패하면 Discord 로 알린다. 하지만 <b>cron 자체가 죽어 아예 실행되지
 * 않는 경우</b>는 스크립트가 알릴 수 없다 — 돌지 않았으니까. 그건 밖에서 보는 수밖에 없고,
 * 그게 이 인디케이터다.
 *
 * <p>백업이 낡았다고 서비스가 고장난 건 아니므로 <b>전체 상태(aggregate)에는 반영하지
 * 않는다</b>({@code SystemHealthService} 의 informational 처리). 여기서 DOWN 이 뜨면
 * "면접이 안 된다"가 아니라 "복구 수단이 없다"는 뜻이다.
 *
 * <p>빈 이름이 곧 Actuator 컴포넌트 키가 된다 — {@code backupHealthIndicator} → {@code "backup"}.
 */
@Component
public class BackupHealthIndicator implements HealthIndicator {

    private final Path markerPath;
    private final Duration maxAge;

    public BackupHealthIndicator(
        @Value("${backup.marker-path:/var/backups/stackup/LAST_SUCCESS}") String markerPath,
        @Value("${backup.max-age-hours:36}") long maxAgeHours
    ) {
        this.markerPath = Path.of(markerPath);
        // 일 1회 백업 + 여유. 26시간이면 한 번 건너뛴 걸 못 잡고, 48시간이면 이틀을 놓친다.
        this.maxAge = Duration.ofHours(maxAgeHours);
    }

    @Override
    public Health health() {
        if (!Files.isReadable(markerPath)) {
            // 로컬 개발에는 마운트가 없어 항상 여기로 온다 — 그래서 aggregate 에서 빼 둔다.
            return Health.down()
                .withDetail("reason", "백업 성공 기록이 없습니다 (한 번도 성공하지 않았거나 마운트 누락)")
                .withDetail("markerPath", markerPath.toString())
                .build();
        }

        Instant lastSuccess;
        try {
            lastSuccess = Instant.parse(Files.readString(markerPath).trim());
        } catch (IOException | DateTimeParseException e) {
            return Health.down()
                .withDetail("reason", "백업 성공 기록을 읽을 수 없습니다: " + e.getClass().getSimpleName())
                .withDetail("markerPath", markerPath.toString())
                .build();
        }

        Duration age = Duration.between(lastSuccess, Instant.now());
        Health.Builder builder = age.compareTo(maxAge) > 0 ? Health.down() : Health.up();
        return builder
            .withDetail("lastSuccess", lastSuccess.toString())
            .withDetail("ageHours", age.toHours())
            .withDetail("maxAgeHours", maxAge.toHours())
            .build();
    }
}
