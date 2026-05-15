package com.stackup.stackup.common.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageEnvelope(
    String messageId,
    String messageType,
    String version,
    String traceId,
    @JsonSerialize(using = ToStringSerializer.class)
    Instant publishedAt,
    String publisher,
    Object payload,
    Map<String, Object> context
) {
}
