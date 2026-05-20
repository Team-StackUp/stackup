package com.stackup.stackup.document.application.dto;

import com.stackup.stackup.common.messaging.MessageContext;
import java.time.Instant;

/**
 * {@link com.stackup.stackup.common.messaging.MessageEnvelope} 의 callback.analysis 전용 구체 타입.
 *
 * generic 타입은 Spring AMQP {@code JacksonJsonMessageConverter} 가 method 선언으로
 * payload 타입을 추론하지 못할 수 있어, listener 시그니처에서는 본 구체 타입을 사용한다.
 */
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
