package com.stackup.stackup.common.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.stackup.stackup.common.trace.TraceContext;

@ExtendWith(MockitoExtension.class)
class RabbitMessagePublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private RabbitMessagePublisher rabbitMessagePublisher;

    @BeforeEach
    void setUp() {
        TraceContext.clear();
    }

    @Test
    void publishToAi_setsEnvelopeMetadataAndRabbitHeaders() {
        TraceContext.setTraceId("trace-123");

        MessageEnvelope<Map<String, Object>> envelope = rabbitMessagePublisher.publishToAi(
            RoutingKeys.ANALYZE_RESUME,
            Map.of("resumeId", 1L),
            MessageContext.ofUser(99L)
        );

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<org.springframework.amqp.core.MessagePostProcessor> postProcessorCaptor =
            ArgumentCaptor.forClass(org.springframework.amqp.core.MessagePostProcessor.class);

        verify(rabbitTemplate).convertAndSend(
            eq(RoutingKeys.CORE_TO_AI_EXCHANGE),
            eq(RoutingKeys.ANALYZE_RESUME),
            payloadCaptor.capture(),
            postProcessorCaptor.capture()
        );

        assertThat(payloadCaptor.getValue()).isEqualTo(envelope);
        assertThat(envelope.messageType()).isEqualTo(RoutingKeys.ANALYZE_RESUME);
        assertThat(envelope.version()).isEqualTo("v1");
        assertThat(envelope.traceId()).isEqualTo("trace-123");
        assertThat(envelope.publishedAt()).isNotNull();
        assertThat(envelope.publisher()).isEqualTo("core-server");
        assertThat(envelope.context().userId()).isEqualTo(99L);

        Message message = postProcessorCaptor.getValue().postProcessMessage(
            new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties())
        );

        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getContentEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(envelope.messageId());
        assertThat(message.getMessageProperties().getCorrelationId()).isEqualTo(envelope.messageId());
        assertThat(message.getMessageProperties().getHeaders()).containsEntry("x-trace-id", "trace-123");
    }
}
