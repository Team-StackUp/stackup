package com.stackup.stackup.system.application.dto;

import java.util.Map;

public record ComponentHealthResponse(
    String name,
    String status,
    Map<String, Object> details
) {
}
