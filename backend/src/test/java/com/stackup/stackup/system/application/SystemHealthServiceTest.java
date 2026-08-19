package com.stackup.stackup.system.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.actuate.endpoint.AdditionalHealthEndpointPath;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.actuate.endpoint.HttpCodeStatusMapper;
import org.springframework.boot.health.actuate.endpoint.SimpleHttpCodeStatusMapper;
import org.springframework.boot.health.actuate.endpoint.SimpleStatusAggregator;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import com.stackup.stackup.system.application.dto.ComponentHealthResponse;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.registry.DefaultHealthContributorRegistry;
import org.springframework.boot.health.registry.DefaultReactiveHealthContributorRegistry;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.health.registry.ReactiveHealthContributorRegistry;
import org.springframework.boot.actuate.endpoint.SecurityContext;

class SystemHealthServiceTest {

    @Test
    void live_returnsUpStatus() {
        SystemHealthService systemHealthService = new SystemHealthService(null);

        var response = systemHealthService.live();

        assertThat(response.status()).isEqualTo(Status.UP.getCode());
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void ready_usesDatabaseAndRabbitmqIndicators() {
        HealthEndpoint healthEndpoint = healthEndpoint(Map.of(
            "db", indicator(Status.UP, Map.of("connections", 12)),
            "rabbit", indicator(Status.UP, Map.of("host", "localhost"))   // Actuator 실제 키
        ));
        SystemHealthService systemHealthService = new SystemHealthService(healthEndpoint);

        var response = systemHealthService.ready();

        assertThat(response.status()).isEqualTo(Status.UP.getCode());
        assertThat(response.components()).containsKeys("database", "rabbitmq");
        assertThat(response.components()).doesNotContainKeys("s3", "aiServer");
    }

    @Test
    void health_includesDatabaseRabbitmqS3AndAiServer() {
        HealthEndpoint healthEndpoint = healthEndpoint(Map.of(
            "db", indicator(Status.UP, Map.of()),
            "rabbit", indicator(Status.DOWN, Map.of("reachable", false))  // Actuator 실제 키
        ));
        SystemHealthService systemHealthService = new SystemHealthService(healthEndpoint);

        var response = systemHealthService.health();

        assertThat(response.status()).isEqualTo(Status.DOWN.getCode());
        assertThat(response.components()).containsKeys("database", "rabbitmq", "s3", "aiServer");
        assertThat(response.components().get("s3").status()).isEqualTo(Status.UNKNOWN.getCode());
        assertThat(response.components().get("aiServer").status()).isEqualTo(Status.UNKNOWN.getCode());
    }

    /**
     * 응답 키와 Actuator 컴포넌트 키가 다르다는 사실 자체를 못 박는다.
     *
     * <p>운영에서 rabbitmq 가 영구 UNKNOWN 이었던 원인이 여기였다 — Actuator 는
     * {@code rabbitHealthContributor} 빈을 "rabbit" 으로 등록하는데 "rabbitmq" 로 조회했다.
     * 기존 테스트는 픽스처를 "rabbitmq" 로 등록해서 같은 실수를 그대로 재현하고 있었다.
     */
    @Test
    void health_readsRabbitFromActuatorKeyNotResponseKey() {
        HealthEndpoint healthEndpoint = healthEndpoint(Map.of(
            "db", indicator(Status.UP, Map.of()),
            "rabbit", indicator(Status.UP, Map.of("version", "3.13"))
        ));
        SystemHealthService systemHealthService = new SystemHealthService(healthEndpoint);

        var response = systemHealthService.ready();

        // 응답 키는 그대로 rabbitmq — 공개 계약은 바뀌지 않는다.
        assertThat(response.components().get("rabbitmq").status()).isEqualTo(Status.UP.getCode());
        assertThat(response.status()).isEqualTo(Status.UP.getCode());
    }

    // Actuator 에 없는 이름으로 조회하면 UNKNOWN 이 된다 — 회귀 시 이 테스트가 아니라
    // 위 테스트가 깨지도록, 여기서는 '없을 때의 동작'만 확인한다.
    @Test
    void health_reportsUnknownWhenActuatorHasNoSuchComponent() {
        HealthEndpoint healthEndpoint = healthEndpoint(Map.of(
            "db", indicator(Status.UP, Map.of())
        ));
        SystemHealthService systemHealthService = new SystemHealthService(healthEndpoint);

        var response = systemHealthService.ready();

        assertThat(response.components().get("rabbitmq").status()).isEqualTo(Status.UNKNOWN.getCode());
        assertThat(response.status()).isEqualTo(Status.UNKNOWN.getCode());
    }

    /**
     * 공개(permitAll) 엔드포인트라 상세를 담지 않는다.
     *
     * <p>Actuator 는 기본값이 {@code show-details: never} 인데 이 서비스가 descriptor 에서
     * 상세를 직접 꺼내 쓰면서 그 보호를 우회하고 있었다 — RabbitMQ 버전·S3 버킷명·큐 이름과
     * 적체량이 인증 없이 나갔다. 상세가 필요하면 호스트에서 /actuator/health 를 본다.
     */
    @Test
    void health_doesNotExposeComponentDetails() {
        HealthEndpoint healthEndpoint = healthEndpoint(Map.of(
            "db", indicator(Status.UP, Map.of("database", "PostgreSQL")),
            "rabbit", indicator(Status.UP, Map.of("version", "4.3.5"))
        ));
        SystemHealthService systemHealthService = new SystemHealthService(healthEndpoint);

        var response = systemHealthService.health();

        // 상태는 그대로 전달되지만 상세는 응답 타입에 아예 없다.
        assertThat(response.components().get("rabbitmq").status()).isEqualTo(Status.UP.getCode());
        assertThat(ComponentHealthResponse.class.getRecordComponents())
            .extracting(java.lang.reflect.RecordComponent::getName)
            .containsExactly("name", "status");
    }

    private static HealthEndpoint healthEndpoint(Map<String, HealthIndicator> indicators) {
        HealthContributorRegistry registry = new DefaultHealthContributorRegistry();
        indicators.forEach(registry::registerContributor);

        ReactiveHealthContributorRegistry reactiveRegistry = new DefaultReactiveHealthContributorRegistry();
        HealthEndpointGroup primaryGroup = new HealthEndpointGroup() {
            @Override
            public boolean isMember(String name) {
                return true;
            }

            @Override
            public boolean showComponents(SecurityContext securityContext) {
                return true;
            }

            @Override
            public boolean showDetails(SecurityContext securityContext) {
                return true;
            }

            @Override
            public StatusAggregator getStatusAggregator() {
                return new SimpleStatusAggregator();
            }

            @Override
            public HttpCodeStatusMapper getHttpCodeStatusMapper() {
                return new SimpleHttpCodeStatusMapper();
            }

            @Override
            public AdditionalHealthEndpointPath getAdditionalPath() {
                return null;
            }
        };

        return new HealthEndpoint(registry, reactiveRegistry, HealthEndpointGroups.of(primaryGroup, Map.of()), Duration.ZERO);
    }

    private static HealthIndicator indicator(Status status, Map<String, Object> details) {
        return () -> Health.status(status).withDetails(details).build();
    }
}
