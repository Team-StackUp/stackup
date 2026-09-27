package com.stackup.stackup.common.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

// 백업 스크립트는 실패하면 Discord 로 알린다. 하지만 cron 이 죽어 **아예 안 돈** 경우는
// 스크립트가 알릴 수 없다 — 그건 밖에서 보는 수밖에 없고 그게 이 인디케이터다.
class BackupHealthIndicatorTest {

    @TempDir Path dir;

    private BackupHealthIndicator indicator(long maxAgeHours) {
        return new BackupHealthIndicator(dir.resolve("LAST_SUCCESS").toString(), maxAgeHours);
    }

    private void writeMarker(Instant at) throws IOException {
        Files.writeString(dir.resolve("LAST_SUCCESS"), at.toString() + "\n");
    }

    @Test
    void 최근_백업이_있으면_UP() throws IOException {
        writeMarker(Instant.now().minus(2, ChronoUnit.HOURS));

        Health health = indicator(36).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("ageHours", 2L);
    }

    @Test
    void 한도를_넘긴_백업은_DOWN() throws IOException {
        // cron 이 죽어 이틀째 안 돈 상황.
        writeMarker(Instant.now().minus(50, ChronoUnit.HOURS));

        Health health = indicator(36).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("ageHours", 50L);
    }

    @Test
    void 경계값_직전은_UP() throws IOException {
        writeMarker(Instant.now().minus(35, ChronoUnit.HOURS));
        assertThat(indicator(36).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void 기록이_없으면_DOWN() {
        // 한 번도 성공하지 않았거나, 마운트가 빠졌거나. 둘 다 "복구 수단 없음" 이다.
        Health health = indicator(36).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("reason").toString()).contains("백업 성공 기록이 없습니다");
    }

    @Test
    void 깨진_기록은_DOWN() throws IOException {
        Files.writeString(dir.resolve("LAST_SUCCESS"), "not-a-timestamp");

        Health health = indicator(36).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("reason").toString()).contains("읽을 수 없습니다");
    }
}
