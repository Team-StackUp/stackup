package com.stackup.stackup.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 스케줄러 스레드 풀이 1로 돌아가지 않도록 지킨다.
 *
 * <p>Spring Boot 기본값은 1이고, 그러면 모든 {@code @Scheduled} 가 한 줄로 선다. 이 서비스의
 * 스케줄 작업 중 셋은 <b>멈춘 면접을 푸는 유일한 장치</b>라서, 느린 작업 하나가
 * (S3/MinIO 를 도는 OrphanedObjectSweeper 가 가장 유력하다) 면접 복구를 통째로 멈춘다.
 *
 * <p>설정 한 줄이 지워져도 컴파일·테스트가 통과하고 <b>운영에서도 아무 증상이 없다가</b>
 * 장애 때만 드러난다 — 그래서 CI 가 대신 본다.
 */
class SchedulingPoolSizeTest {

    @Test
    void applicationYml_declaresSchedulingPoolSizeGreaterThanOne() throws IOException {
        String yml = read("application.yml");

        Matcher m = Pattern.compile("size:\\s*\\$\\{SCHEDULING_POOL_SIZE:(\\d+)}").matcher(yml);
        assertThat(m.find())
            .as("spring.task.scheduling.pool.size 설정이 사라졌다 — 기본값 1로 되돌아간다")
            .isTrue();
        assertThat(Integer.parseInt(m.group(1)))
            .as("스케줄 작업이 직렬화되지 않을 만큼은 돼야 한다")
            .isGreaterThan(1);
    }

    private String read(String name) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as(name + " 을 찾을 수 없다").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
