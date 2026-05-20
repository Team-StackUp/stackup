package com.stackup.stackup.common.messaging;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.trace.TraceContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Core → RealTime 알림 발행자.
 * stackup.realtime exchange / routing key realtime.session.notify 로 publish.
 *
 * RealTime 서버는 q.realtime.session.notify 큐 consumer 로 envelope.context.sessionId 가
 * 가리키는 SSE 구독자에게 fan-out 한다 (docs/messaging.md §5.12).
 */
@Component
public class RealtimeNotifyPublisher {

    public record RealtimeNotifyPayload(String eventType, Object data) {
    }

    private final RabbitMqProperties properties;
    private final RabbitTemplate rabbitTemplate;

    public RealtimeNotifyPublisher(RabbitMqProperties properties, RabbitTemplate rabbitTemplate) {
        this.properties = properties;
        this.rabbitTemplate = rabbitTemplate;
    }

    public MessageEnvelope<RealtimeNotifyPayload> publishSessionNotify(
        long sessionId,
        Long userId,
        String eventType,
        Object data
    ) {
        var payload = new RealtimeNotifyPayload(eventType, data);
        var context = new MessageContext(userId, sessionId, null, null);
        var envelope = new MessageEnvelope<>(
            UUID.randomUUID().toString(),
            "realtime.session.notify",
            properties.version(),
            TraceContext.getTraceId(),
            Instant.now(),
            properties.publisher(),
            payload,
            context
        );

        rabbitTemplate.convertAndSend(
            properties.exchanges().names().realtime(),
            properties.routingKeys().realtimeSessionNotify(),
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
            message.getMessageProperties().setHeader(properties.message().traceIdHeader(), envelope.traceId());
            return message;
        };
    }
}
