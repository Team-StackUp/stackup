package com.stackup.stackup.common.sse;

import com.stackup.stackup.common.trace.TraceContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SseEventPublisher {

    private final SseEmitterRegistry registry;

    public SseEventPublisher(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    public SseEvent publishToUser(Long userId, SseEventType type, Object payload) {
        SseEvent event = new SseEvent(UUID.randomUUID().toString(), type, payload, Instant.now(), TraceContext.getTraceId());
        registry.sendToUser(userId, event);
        return event;
    }

    public SseEvent publishToSession(Long sessionId, SseEventType type, Object payload) {
        SseEvent event = new SseEvent(UUID.randomUUID().toString(), type, payload, Instant.now(), TraceContext.getTraceId());
        registry.sendToSession(sessionId, event);
        return event;
    }

    public SseEvent publishToDocument(Long documentId, SseEventType type, Object payload) {
        SseEvent event = new SseEvent(UUID.randomUUID().toString(), type, payload, Instant.now(), TraceContext.getTraceId());
        registry.sendToDocument(documentId, event);
        return event;
    }
}
