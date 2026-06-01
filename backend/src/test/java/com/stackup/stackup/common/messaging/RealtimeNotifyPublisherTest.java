package com.stackup.stackup.common.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.sse.SseEventType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class RealtimeNotifyPublisherTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) RabbitMqProperties properties;
    @Mock RabbitTemplate rabbitTemplate;
    @InjectMocks RealtimeNotifyPublisher publisher;

    @Test
    void publishToUser_sendsUserNotifyEnvelope() {
        when(properties.exchanges().names().realtime()).thenReturn("stackup.realtime");
        when(properties.routingKeys().realtimeUserNotify()).thenReturn("realtime.user.notify");
        when(properties.version()).thenReturn("v1");
        when(properties.publisher()).thenReturn("core-server");

        publisher.publishToUser(42L, SseEventType.DOC_STATE, Map.of("state", "PROCESSING"));

        ArgumentCaptor<MessageEnvelope> cap = ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(rabbitTemplate).convertAndSend(eq("stackup.realtime"), eq("realtime.user.notify"),
            cap.capture(), any(MessagePostProcessor.class));
        MessageEnvelope<?> env = cap.getValue();
        assertThat(env.messageType()).isEqualTo("realtime.user.notify");
        assertThat(env.context().userId()).isEqualTo(42L);
        assertThat(env.context().sessionId()).isNull();
        RealtimeNotifyPublisher.RealtimeNotifyPayload payload =
            (RealtimeNotifyPublisher.RealtimeNotifyPayload) env.payload();
        assertThat(payload.eventType()).isEqualTo("DOC_STATE");
    }

    @Test
    void publishToSession_sendsSessionNotifyEnvelopeWithSessionContext() {
        when(properties.exchanges().names().realtime()).thenReturn("stackup.realtime");
        when(properties.routingKeys().realtimeSessionNotify()).thenReturn("realtime.session.notify");
        when(properties.version()).thenReturn("v1");
        when(properties.publisher()).thenReturn("core-server");

        publisher.publishToSession(99L, SseEventType.SESSION_MESSAGE, 503L);

        ArgumentCaptor<MessageEnvelope> cap = ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(rabbitTemplate).convertAndSend(eq("stackup.realtime"), eq("realtime.session.notify"),
            cap.capture(), any(MessagePostProcessor.class));
        MessageEnvelope<?> env = cap.getValue();
        assertThat(env.context().sessionId()).isEqualTo(99L);
        assertThat(env.context().userId()).isNull();
    }

    @Test
    void publishToDocument_sendsDocumentNotifyEnvelopeWithDocumentContext() {
        when(properties.exchanges().names().realtime()).thenReturn("stackup.realtime");
        when(properties.routingKeys().realtimeDocumentNotify()).thenReturn("realtime.document.notify");
        when(properties.version()).thenReturn("v1");
        when(properties.publisher()).thenReturn("core-server");

        publisher.publishToDocument(101L, SseEventType.DOC_STATE, Map.of("k", "v"));

        ArgumentCaptor<MessageEnvelope> cap = ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(rabbitTemplate).convertAndSend(eq("stackup.realtime"), eq("realtime.document.notify"),
            cap.capture(), any(MessagePostProcessor.class));
        assertThat(cap.getValue().context().documentId()).isEqualTo(101L);
    }
}
