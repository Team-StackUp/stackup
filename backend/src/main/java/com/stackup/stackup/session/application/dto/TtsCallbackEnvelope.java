package com.stackup.stackup.session.application.dto;

import com.stackup.stackup.common.messaging.MessageContext;
import java.time.Instant;

public record TtsCallbackEnvelope(
    String messageId,
    String messageType,
    String version,
    String traceId,
    Instant publishedAt,
    String publisher,
    TtsCallbackPayload payload,
    MessageContext context
) {
}
