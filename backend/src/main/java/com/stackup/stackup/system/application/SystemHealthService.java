package com.stackup.stackup.system.application;

import com.stackup.stackup.system.application.dto.ComponentHealthResponse;
import com.stackup.stackup.system.application.dto.SystemHealthResponse;
import com.stackup.stackup.system.application.dto.SystemLiveResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Service;

@Service
public class SystemHealthService {

    // 응답 키(name)와 Actuator 컴포넌트 키(actuatorPath)는 다르다.
    // Actuator 키는 Spring 이 등록하는 빈 이름에서 접미사를 뗀 값이다 —
    // DataSourceHealthContributor→"db", rabbitHealthContributor→"rabbit".
    // "rabbitmq" 로 조회하면 항상 null 이라 rabbitmq 컴포넌트가 영구 UNKNOWN 이 된다.
    private static final ComponentSpec DATABASE = new ComponentSpec("database", "db");
    private static final ComponentSpec RABBITMQ = new ComponentSpec("rabbitmq", "rabbit");
    private static final ComponentSpec S3 = new ComponentSpec("s3", "s3");
    private static final ComponentSpec AI_SERVER = new ComponentSpec("aiServer", "aiServer");

    private final HealthEndpoint healthEndpoint;

    public SystemHealthService(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    public SystemLiveResponse live() {
        return new SystemLiveResponse(Status.UP.getCode(), Instant.now());
    }

    public SystemHealthResponse ready() {
        return buildResponse(List.of(DATABASE, RABBITMQ));
    }

    public SystemHealthResponse health() {
        return buildResponse(List.of(DATABASE, RABBITMQ, S3, AI_SERVER));
    }

    private SystemHealthResponse buildResponse(List<ComponentSpec> specs) {
        Map<String, ComponentHealthResponse> components = new LinkedHashMap<>();
        for (ComponentSpec spec : specs) {
            components.put(spec.name(), resolveComponent(spec));
        }
        return new SystemHealthResponse(
            aggregateStatus(components.values()),
            Instant.now(),
            components
        );
    }

    private ComponentHealthResponse resolveComponent(ComponentSpec spec) {
        HealthDescriptor descriptor = resolveDescriptor(spec.actuatorPath());
        if (descriptor == null) {
            return new ComponentHealthResponse(spec.name(), Status.UNKNOWN.getCode());
        }
        return new ComponentHealthResponse(spec.name(), descriptor.getStatus().getCode());
    }

    protected HealthDescriptor resolveDescriptor(String path) {
        try {
            return healthEndpoint.healthForPath(path);
        } catch (RuntimeException ex) {
            return null;
        }
    }


    private String aggregateStatus(Iterable<ComponentHealthResponse> components) {
        boolean hasUnknown = false;
        for (ComponentHealthResponse component : components) {
            String status = component.status();
            if (Status.DOWN.getCode().equals(status) || Status.OUT_OF_SERVICE.getCode().equals(status)) {
                return Status.DOWN.getCode();
            }
            if (!Status.UP.getCode().equals(status)) {
                hasUnknown = true;
            }
        }
        return hasUnknown ? Status.UNKNOWN.getCode() : Status.UP.getCode();
    }

    private record ComponentSpec(String name, String actuatorPath) {
    }
}
