package com.stackup.stackup.session.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 직군 목록은 <b>네 군데</b>에 흩어져 있다 — 이 enum, Flyway CHECK 제약, AI 서버의
 * Pydantic Literal, 프론트 선택지. 하나만 빠뜨리면 그 직군으로 만든 세션이 저장에서
 * 터지거나(CHECK) 메시지 검증에서 튕겨 조용히 DLQ 로 간다.
 *
 * <p>사람이 네 곳을 동시에 맞추는 일을 믿지 않는다. 여기서는 enum ↔ 마이그레이션을
 * 대조하고, Python Literal 대조는 AI 쪽 테스트가 같은 파일을 읽어 맡는다.
 */
class JobCategoryTest {

    private static final Path MIGRATION = Path.of(
        "src/main/resources/db/migration/V35__extend_job_categories.sql");

    @Test
    void enum_과_마이그레이션_CHECK_목록이_같다() throws IOException {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        Matcher m = Pattern.compile("'([A-Z_]+)'").matcher(sql);
        Set<String> inSql = m.results().map(r -> r.group(1)).collect(Collectors.toSet());
        Set<String> inEnum = Arrays.stream(JobCategory.values())
            .map(Enum::name).collect(Collectors.toSet());

        assertThat(inSql)
            .as("CHECK 제약과 enum 이 어긋나면 그 직군 세션의 INSERT 가 터진다")
            .isEqualTo(inEnum);
    }

    @Test
    void 모든_직군에_한국어_라벨이_있다() {
        for (JobCategory c : JobCategory.values()) {
            // DBA 처럼 한국어 표기가 없는 직군은 이름이 곧 라벨이라 같아도 된다.
            assertThat(c.koreanLabel())
                .as("%s 의 한국어 라벨", c)
                .isNotBlank();
        }
    }

    @Test
    void 기존_개발_직군_이름은_바뀌지_않는다() {
        // enum 이름이 DB 에 문자열로 저장된다 — 바꾸면 이미 쌓인 세션·통계가 깨진다.
        assertThat(JobCategory.valueOf("FRONTEND")).isNotNull();
        assertThat(JobCategory.valueOf("BACKEND")).isNotNull();
        assertThat(JobCategory.valueOf("INFRA")).isNotNull();
        assertThat(JobCategory.valueOf("DBA")).isNotNull();
    }

    @Test
    void 비개발_직군이_포함되어_있다() {
        // 서비스 대상이 IT 직군이 아니라 취준생 전반이다.
        long nonEngineering = Arrays.stream(JobCategory.values())
            .filter(c -> !c.isEngineering()).count();

        assertThat(nonEngineering)
            .as("비개발 직군이 없으면 개발자가 아닌 지원자는 고를 수 있는 게 없다")
            .isGreaterThanOrEqualTo(10);
    }

    @Test
    void isEngineering_이_개발_직군만_참이다() {
        assertThat(JobCategory.BACKEND.isEngineering()).isTrue();
        assertThat(JobCategory.DATA_AI.isEngineering()).isTrue();
        assertThat(JobCategory.SALES.isEngineering()).isFalse();
        assertThat(JobCategory.HR.isEngineering()).isFalse();
    }
}
