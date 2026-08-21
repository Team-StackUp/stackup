package com.stackup.stackup.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * 리포지토리 테스트가 공유하는 PostgreSQL 컨테이너.
 *
 * <p>static 으로 한 번 띄우고 JVM 종료까지 재사용한다. Testcontainers 의 Ryuk 이
 * 프로세스가 죽을 때 정리하므로 stop 을 명시적으로 부르지 않는다 — 클래스마다
 * 껐다 켜면 리포지토리 테스트가 늘어날수록 CI 가 선형으로 느려진다.
 *
 * <p>이미지는 `infra/postgres` 와 같은 pgvector 계열이어야 한다. 마이그레이션이
 * vector 타입·인덱스를 쓰기 때문에 순정 postgres 이미지로는 Flyway 가 실패한다.
 */
public final class PostgresTestContainer {

    // infra/postgres/Dockerfile 과 같은 태그를 쓴다. 운영 PG 버전을 올리면 여기도 같이 올린다.
    private static final DockerImageName IMAGE = DockerImageName.parse("pgvector/pgvector:pg17")
        .asCompatibleSubstituteFor("postgres");

    private static final PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>(IMAGE)
        .withDatabaseName("stackup")
        .withUsername("stackup")
        .withPassword("stackup")
        // Flyway 가 돌기 전에 확장이 있어야 한다. 엔트리포인트 초기화 스크립트로 넣는다
        // (infra/postgres 가 init.sql 을 같은 자리에 복사하는 것과 같은 방식).
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("db/testcontainers-init.sql"),
            "/docker-entrypoint-initdb.d/init.sql");

    private PostgresTestContainer() {
    }

    static PostgreSQLContainer<?> started() {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        return CONTAINER;
    }

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            PostgreSQLContainer<?> pg = started();
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                context,
                "spring.datasource.url=" + pg.getJdbcUrl(),
                "spring.datasource.username=" + pg.getUsername(),
                "spring.datasource.password=" + pg.getPassword(),
                "spring.datasource.driver-class-name=org.postgresql.Driver");
        }
    }
}
