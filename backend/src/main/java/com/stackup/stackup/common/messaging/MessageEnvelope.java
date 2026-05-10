package com.stackup.stackup.common.messaging;

import java.time.Instant;

public record MessageEnvelope<T>(
    String messageId,
    String messageType,
    String version,
    String traceId,
    Instant publishedAt,
    String publisher,
    T payload,
    MessageContext context
) {
}
