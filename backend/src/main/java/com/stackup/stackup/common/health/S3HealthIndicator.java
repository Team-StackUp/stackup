package com.stackup.stackup.common.health;

import com.stackup.stackup.common.config.properties.S3Properties;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 객체 스토리지(S3/MinIO) 도달성.
 *
 * <p>빈 이름이 곧 Actuator 컴포넌트 키가 된다 — {@code s3HealthIndicator} → {@code "s3"}.
 * `SystemHealthService` 가 그 키로 조회하므로 이름을 바꾸면 UNKNOWN 으로 돌아간다.
 *
 * <p>스토리지가 죽으면 이력서 업로드·음성 답변·TTS 재생이 전부 실패한다.
 */
@Component
public class S3HealthIndicator implements HealthIndicator {

    private final ObjectStorageClient storage;
    private final S3Properties properties;

    public S3HealthIndicator(ObjectStorageClient storage, S3Properties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    @Override
    public Health health() {
        try {
            storage.verifyAvailable();
            return Health.up().withDetail("bucket", properties.bucket()).build();
        } catch (RuntimeException e) {
            return Health.down()
                .withDetail("bucket", properties.bucket())
                .withDetail("reason", e.getMessage())
                .build();
        }
    }
}
