package com.stackup.stackup.system.application;

import com.stackup.stackup.system.application.dto.ComponentHealthResponse;
import com.stackup.stackup.system.application.dto.SystemHealthResponse;
import com.stackup.stackup.system.application.dto.SystemLiveResponse;
import java.time.Instant;
import java.util.ArrayList;
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
    private static final ComponentSpec DATABASE = new ComponentSpec("database", "db", false);
    private static final ComponentSpec RABBITMQ = new ComponentSpec("rabbitmq", "rabbit", false);
    private static final ComponentSpec S3 = new ComponentSpec("s3", "s3", false);
    private static final ComponentSpec AI_SERVER = new ComponentSpec("aiServer", "aiServer", false);
    // 백업이 낡아도 서비스는 정상 동작한다 — 전체 상태를 끌어내리면 "면접이 안 된다"와
    // "복구 수단이 없다"가 구분되지 않고, /health 를 보는 업타임 감시가 헛울린다.
    // 값은 보여주되 aggregate 에서는 뺀다.
    private static final ComponentSpec BACKUP = new ComponentSpec("backup", "backup", true);

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
        return buildResponse(List.of(DATABASE, RABBITMQ, S3, AI_SERVER, BACKUP));
    }

    private SystemHealthResponse buildResponse(List<ComponentSpec> specs) {
        Map<String, ComponentHealthResponse> components = new LinkedHashMap<>();
        List<ComponentHealthResponse> aggregated = new ArrayList<>();
        for (ComponentSpec spec : specs) {
            ComponentHealthResponse component = resolveComponent(spec);
            components.put(spec.name(), component);
            if (!spec.informational()) {
                aggregated.add(component);
            }
        }
        return new SystemHealthResponse(
            aggregateStatus(aggregated),
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

    // informational=true 면 응답에는 싣되 전체 상태 집계에서는 제외한다.
    private record ComponentSpec(String name, String actuatorPath, boolean informational) {
    }
}
