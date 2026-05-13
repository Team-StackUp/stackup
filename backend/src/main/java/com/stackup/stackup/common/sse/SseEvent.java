package com.stackup.stackup.common.sse;

import java.time.Instant;

public record SseEvent(
    String id,
    SseEventType type,
    Object payload,
    Instant timestamp,
    String traceId
) {
}
