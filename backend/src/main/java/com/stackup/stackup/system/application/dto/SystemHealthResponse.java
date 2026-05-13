package com.stackup.stackup.system.application.dto;

import java.time.Instant;
import java.util.Map;

public record SystemHealthResponse(
    String status,
    Instant timestamp,
    Map<String, ComponentHealthResponse> components
) {
}
