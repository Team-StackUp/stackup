package com.stackup.stackup.session.application.dto;

import com.stackup.stackup.common.messaging.MessageContext;
import java.time.Instant;

// MessageEnvelope<QuestionsCallbackPayload> 의 구체 타입 (JacksonJsonMessageConverter generic 추론용).
public record QuestionsCallbackEnvelope(
    String messageId,
    String messageType,
    String version,
    String traceId,
    Instant publishedAt,
    String publisher,
    QuestionsCallbackPayload payload,
    MessageContext context
) {
}
