package com.stackup.stackup.common.response;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
    String code,
    String message,
    String traceId,
    Instant timestamp,
    Map<String, Object> details
) {
}
