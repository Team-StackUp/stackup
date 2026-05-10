package com.stackup.stackup.common.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.stackup.stackup.common.trace.TraceContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SseEventPublisherTest {

    @Mock
    private SseEmitterRegistry registry;

    @InjectMocks
    private SseEventPublisher sseEventPublisher;

    @BeforeEach
    void setUp() {
        TraceContext.clear();
    }

    @Test
    void publishToUser_wrapsPayloadWithTraceMetadata() {
        TraceContext.setTraceId("trace-777");

        sseEventPublisher.publishToUser(7L, SseEventType.SESSION_STATE, Map.of("state", "READY"));

        ArgumentCaptor<SseEvent> eventCaptor = ArgumentCaptor.forClass(SseEvent.class);
        verify(registry).sendToUser(eq(7L), eventCaptor.capture());

        SseEvent event = eventCaptor.getValue();
        assertThat(event.id()).isNotBlank();
        assertThat(event.type()).isEqualTo(SseEventType.SESSION_STATE);
        assertThat(event.payload()).isEqualTo(Map.of("state", "READY"));
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.traceId()).isEqualTo("trace-777");
    }
}
