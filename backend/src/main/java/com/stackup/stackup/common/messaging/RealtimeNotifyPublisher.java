package com.stackup.stackup.common.messaging;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.common.trace.TraceContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

// Core -> RealTime 서버 알림 발행. RealTime 이 SSE/WS 구독자에게 fan-out 한다.
// 채널별 messageType(realtime.{session|user|document}.notify)으로 발행하며,
// RealTime 의 bridge.Envelope.Channel() 이 messageType + context 로 채널을 판별한다.
// 호출은 콜백 서비스의 @Transactional 안에서 DB 영속 이후 일어나므로, SSE 페이로드의 ID 는
// 프론트가 조회 가능한 상태가 보장된다.
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

    public void publishToSession(Long sessionId, SseEventType type, Object data) {
        publish(properties.routingKeys().realtimeSessionNotify(),
            new MessageContext(null, sessionId, null, null), type, data);
    }

    public void publishToUser(Long userId, SseEventType type, Object data) {
        publish(properties.routingKeys().realtimeUserNotify(),
            new MessageContext(userId, null, null, null), type, data);
    }

    public void publishToDocument(Long documentId, SseEventType type, Object data) {
        publish(properties.routingKeys().realtimeDocumentNotify(),
            new MessageContext(null, null, documentId, null), type, data);
    }

    private void publish(String routingKey, MessageContext context, SseEventType type, Object data) {
        var payload = new RealtimeNotifyPayload(type.name(), data);
        // messageType == routing key (realtime.{kind}.notify) — RealTime 이 이 값으로 채널을 판별한다.
        var envelope = new MessageEnvelope<>(
            UUID.randomUUID().toString(),
            routingKey,
            properties.version(),
            TraceContext.getTraceId(),
            Instant.now(),
            properties.publisher(),
            payload,
            context
        );

        rabbitTemplate.convertAndSend(
            properties.exchanges().names().realtime(),
            routingKey,
            envelope,
            withEnvelopeHeaders(envelope)
        );
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
