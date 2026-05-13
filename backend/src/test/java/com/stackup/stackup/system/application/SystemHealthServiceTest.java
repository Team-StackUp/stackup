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
            "rabbitmq", indicator(Status.UP, Map.of("host", "localhost"))
        ));
        SystemHealthService systemHealthService = new SystemHealthService(healthEndpoint);

        var response = systemHealthService.ready();

        assertThat(response.status()).isEqualTo(Status.UP.getCode());
        assertThat(response.components()).containsKeys("database", "rabbitmq");
        assertThat(response.components()).doesNotContainKeys("s3", "aiServer");
        assertThat(response.components().get("database").details()).containsEntry("connections", 12);
    }

    @Test
    void health_includesDatabaseRabbitmqS3AndAiServer() {
        HealthEndpoint healthEndpoint = healthEndpoint(Map.of(
            "db", indicator(Status.UP, Map.of()),
            "rabbitmq", indicator(Status.DOWN, Map.of("reachable", false))
        ));
        SystemHealthService systemHealthService = new SystemHealthService(healthEndpoint);

        var response = systemHealthService.health();

        assertThat(response.status()).isEqualTo(Status.DOWN.getCode());
        assertThat(response.components()).containsKeys("database", "rabbitmq", "s3", "aiServer");
        assertThat(response.components().get("s3").status()).isEqualTo(Status.UNKNOWN.getCode());
        assertThat(response.components().get("aiServer").status()).isEqualTo(Status.UNKNOWN.getCode());
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
