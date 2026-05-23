package com.stackup.stackup.document.application.dto;

import com.stackup.stackup.common.messaging.MessageContext;
import java.time.Instant;

public record AnalysisCallbackEnvelope(
    String messageId,
    String messageType,
    String version,
    String traceId,
    Instant publishedAt,
    String publisher,
    AnalysisCallbackPayload payload,
    MessageContext context
) {
}
