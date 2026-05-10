package com.stackup.stackup.common.messaging;

import com.stackup.stackup.common.trace.TraceContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitMessagePublisher {

    private static final String VERSION = "v1";
    private static final String PUBLISHER = "core-server";

    private final RabbitTemplate rabbitTemplate;

    public RabbitMessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public <T> MessageEnvelope<T> publishToAi(String routingKey, T payload, MessageContext context) {
        MessageEnvelope<T> envelope = new MessageEnvelope<>(
            UUID.randomUUID().toString(),
            routingKey,
            VERSION,
            TraceContext.getTraceId(),
            Instant.now(),
            PUBLISHER,
            payload,
            context == null ? MessageContext.empty() : context
        );

        rabbitTemplate.convertAndSend(
            RoutingKeys.CORE_TO_AI_EXCHANGE,
            routingKey,
            envelope,
            withEnvelopeHeaders(envelope)
        );
        return envelope;
    }

    private MessagePostProcessor withEnvelopeHeaders(MessageEnvelope<?> envelope) {
        return message -> {
            message.getMessageProperties().setContentType("application/json");
            message.getMessageProperties().setContentEncoding(StandardCharsets.UTF_8.name());
            message.getMessageProperties().setMessageId(envelope.messageId());
            message.getMessageProperties().setCorrelationId(envelope.messageId());
            message.getMessageProperties().setHeader("x-trace-id", envelope.traceId());
            return message;
        };
    }
}
