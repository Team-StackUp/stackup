package com.stackup.stackup.common.messaging;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.trace.TraceContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitMessagePublisher {

    private final RabbitMqProperties properties;
    private final RabbitTemplate rabbitTemplate;

    public RabbitMessagePublisher(RabbitMqProperties properties, RabbitTemplate rabbitTemplate) {
        this.properties = properties;
        this.rabbitTemplate = rabbitTemplate;
    }

    public <T> MessageEnvelope<T> publishToAi(String routingKey, T payload, MessageContext context) {
        String traceId = TraceContext.getTraceId();
        if (traceId == null || traceId.isBlank()) {
            // RabbitListener / EventListener 등에서 MDC 미주입 흐름 — 발행 시점에 새 traceId 생성.
            traceId = UUID.randomUUID().toString();
        }
        MessageEnvelope<T> envelope = new MessageEnvelope<>(
            UUID.randomUUID().toString(),
            routingKey,
            properties.version(),
            traceId,
            Instant.now(),
            properties.publisher(),
            payload,
            context == null ? MessageContext.empty() : context
        );

        rabbitTemplate.convertAndSend(
            properties.exchanges().names().coreToAi(),
            routingKey,
            envelope,
            withEnvelopeHeaders(envelope)
        );
        return envelope;
    }

    private MessagePostProcessor withEnvelopeHeaders(MessageEnvelope<?> envelope) {
        return message -> {
            message.getMessageProperties().setContentType(properties.message().contentType());
            message.getMessageProperties().setContentEncoding(properties.message().contentEncoding());
            message.getMessageProperties().setMessageId(envelope.messageId());
            message.getMessageProperties().setCorrelationId(envelope.messageId());
            message.getMessageProperties().setHeader(properties.message().traceIdHeader(), envelope.traceId());
            return message;
        };
    }
}
