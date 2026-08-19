package com.stackup.stackup.common.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import com.stackup.stackup.common.config.properties.S3Properties;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.common.storage.StorageErrorType;
import com.stackup.stackup.common.storage.StorageException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

@ExtendWith(MockitoExtension.class)
class S3HealthIndicatorTest {

    @Mock ObjectStorageClient storage;

    @Test
    void up_whenStorageIsReachable() {
        Health health = new S3HealthIndicator(storage, properties()).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("bucket", "stackup");
    }

    // 스토리지가 죽으면 이력서 업로드·음성 답변·TTS 재생이 전부 실패한다 — 조용히 UP 이면 안 된다.
    @Test
    void down_whenStorageIsUnreachable() {
        doThrow(new StorageException(StorageErrorType.UNAVAILABLE, "connection refused"))
            .when(storage).verifyAvailable();

        Health health = new S3HealthIndicator(storage, properties()).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("reason").toString()).contains("connection refused");
    }

    private S3Properties properties() {
        // record 순서: endpoint, accessKey, secretKey, bucket, region, pathStyle
        return new S3Properties(
            URI.create("http://localhost:9000"), "key", "secret", "stackup", "us-east-1", true);
    }
}
