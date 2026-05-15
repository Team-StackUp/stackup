package com.stackup.stackup.common.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.trace.TraceContext;

@ExtendWith(MockitoExtension.class)
class RabbitMessagePublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RabbitMessagePublisher rabbitMessagePublisher;

    @BeforeEach
    void setUp() {
        TraceContext.clear();
        rabbitMessagePublisher = new RabbitMessagePublisher(rabbitMqProperties(), rabbitTemplate);
    }

    @Test
    void publishToAi_setsEnvelopeMetadataAndRabbitHeaders() {
        TraceContext.setTraceId("trace-123");

        MessageEnvelope envelope = rabbitMessagePublisher.publishToAi(
            "analyze.resume",
            Map.of("resumeId", 1L),
            Map.of("userId", 99L)
        );

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<org.springframework.amqp.core.MessagePostProcessor> postProcessorCaptor =
            ArgumentCaptor.forClass(org.springframework.amqp.core.MessagePostProcessor.class);

        verify(rabbitTemplate).convertAndSend(
            eq("stackup.core-to-ai"),
            eq("analyze.resume"),
            payloadCaptor.capture(),
            postProcessorCaptor.capture()
        );

        assertThat(payloadCaptor.getValue()).isEqualTo(envelope);
        assertThat(envelope.messageType()).isEqualTo("analyze.resume");
        assertThat(envelope.version()).isEqualTo("v1");
        assertThat(envelope.traceId()).isEqualTo("trace-123");
        assertThat(envelope.publishedAt()).isNotNull();
        assertThat(envelope.publisher()).isEqualTo("core-server");
        assertThat(envelope.context()).containsEntry("userId", 99L);

        Message message = postProcessorCaptor.getValue().postProcessMessage(
            new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties())
        );

        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getContentEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(envelope.messageId());
        assertThat(message.getMessageProperties().getCorrelationId()).isEqualTo(envelope.messageId());
        assertThat(message.getMessageProperties().getHeaders()).containsEntry("x-trace-id", "trace-123");
    }

    private RabbitMqProperties rabbitMqProperties() {
        return new RabbitMqProperties(
            "core-server",
            "v1",
            new RabbitMqProperties.Message("application/json", StandardCharsets.UTF_8.name(), "x-trace-id"),
            new RabbitMqProperties.Template(true),
            new RabbitMqProperties.Exchanges(
                true,
                false,
                new RabbitMqProperties.Exchanges.Names("stackup.core-to-ai", "stackup.ai-to-core")
            ),
            new RabbitMqProperties.Queues(
                true,
                new RabbitMqProperties.Queues.Names(
                    "ai.analyze.resume",
                    "ai.analyze.repository",
                    "ai.generate.questions",
                    "ai.generate.followup",
                    "core.callback.analysis",
                    "core.callback.questions"
                )
            ),
            new RabbitMqProperties.RoutingKeyProperties(
                "analyze.resume",
                "analyze.repository",
                "generate.questions",
                "generate.followup",
                "callback.analysis",
                "callback.questions"
            )
        );
    }
}
