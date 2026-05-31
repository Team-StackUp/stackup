package com.stackup.stackup.session.application.dto;

import com.stackup.stackup.common.messaging.MessageContext;
import java.time.Instant;

public record VoiceCallbackEnvelope(
    String messageId,
    String messageType,
    String version,
    String traceId,
    Instant publishedAt,
    String publisher,
    VoiceCallbackPayload payload,
    MessageContext context
) {
}
