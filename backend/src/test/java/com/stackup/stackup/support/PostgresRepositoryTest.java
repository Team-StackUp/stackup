package com.stackup.stackup.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

/**
 * 실제 PostgreSQL(+pgvector) 을 띄워 리포지토리 쿼리를 검증하는 테스트에 붙인다.
 *
 * <p>왜 필요한가 — 이 프로젝트의 기본 테스트 프로파일은 DataSource·Hibernate·Flyway
 * 오토컨피그를 제외하고 모든 리포지토리를 목으로 대체한다. 빠르지만 그 대가로
 * **`@Query` 의 JPQL 이 아무 검증도 받지 못한다.** 문법 오류는 물론이고
 * "삭제된 행을 안 걸렀다" 같은 의미 결함도 통과한다(실제로 통계 쿼리 5개가 그랬다).
 *
 * <p>컨테이너는 클래스가 아니라 JVM 단위로 하나만 뜬다({@link PostgresTestContainer}) —
 * 리포지토리 테스트가 늘어나도 기동 비용은 한 번이다.
 *
 * <p>스키마는 운영과 같은 Flyway 마이그레이션으로 만든다. `ddl-auto: validate` 라
 * 엔티티 매핑과 마이그레이션이 어긋나면 컨텍스트 로딩에서 바로 터진다 —
 * 이것만으로도 배포 후에야 알게 되던 사고를 CI 로 당긴다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@DataJpaTest
// 임베디드 DB 로 갈아끼우지 않는다 — 우리가 띄운 컨테이너를 그대로 쓴다.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(initializers = PostgresTestContainer.Initializer.class)
// application-test.yml 의 제외 목록을 이 테스트에서만 푼다(거기서 DataSource·Hibernate·
// Flyway 를 빼기 때문에, 그대로 두면 리포지토리 빈 자체가 만들어지지 않는다).
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true",
})
public @interface PostgresRepositoryTest {
}
