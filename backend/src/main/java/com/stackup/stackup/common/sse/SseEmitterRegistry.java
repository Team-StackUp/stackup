package com.stackup.stackup.common.sse;

import com.stackup.stackup.common.config.properties.SseProperties;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseEmitterRegistry {

    private final SseProperties sseProperties;
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> userEmitters = new ConcurrentHashMap<>();
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> sessionEmitters = new ConcurrentHashMap<>();
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> documentEmitters = new ConcurrentHashMap<>();

    public SseEmitterRegistry(SseProperties sseProperties) {
        this.sseProperties = sseProperties;
    }

    public SseEmitter registerUser(Long userId) {
        return register(userEmitters, userId);
    }

    public SseEmitter registerSession(Long sessionId) {
        return register(sessionEmitters, sessionId);
    }

    public SseEmitter registerDocument(Long documentId) {
        return register(documentEmitters, documentId);
    }

    public void sendToUser(Long userId, SseEvent event) {
        send(userEmitters.get(userId), event);
    }

    public void sendToSession(Long sessionId, SseEvent event) {
        send(sessionEmitters.get(sessionId), event);
    }

    public void sendToDocument(Long documentId, SseEvent event) {
        send(documentEmitters.get(documentId), event);
    }

    public void remove(SseEmitter emitter) {
        if (emitter == null) {
            return;
        }
        removeFrom(userEmitters, emitter);
        removeFrom(sessionEmitters, emitter);
        removeFrom(documentEmitters, emitter);
    }

    public void keepAliveAll() {
        SseEvent event = new SseEvent(null, SseEventType.KEEP_ALIVE, null, java.time.Instant.now(), null);
        userEmitters.values().forEach(emitters -> send(emitters, event));
        sessionEmitters.values().forEach(emitters -> send(emitters, event));
        documentEmitters.values().forEach(emitters -> send(emitters, event));
    }

    private SseEmitter register(Map<Long, CopyOnWriteArrayList<SseEmitter>> registry, Long key) {
        Objects.requireNonNull(key, "key must not be null");

        SseEmitter emitter = new SseEmitter(sseProperties.timeout().toMillis());
        registry.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(emitter));
        emitter.onTimeout(() -> remove(emitter));
        emitter.onError(throwable -> remove(emitter));

        sendInitialEvent(emitter);
        return emitter;
    }

    private void sendInitialEvent(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                .name(SseEventType.KEEP_ALIVE.name())
                .data(new SseEvent(null, SseEventType.KEEP_ALIVE, "connected", java.time.Instant.now(), null),
                    MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            remove(emitter);
        }
    }

    private void send(CopyOnWriteArrayList<SseEmitter> emitters, SseEvent event) {
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                // SseEventBuilder.id(null) 호출 시 Spring 내부에서 String.indexOf(int) NPE.
                // keep-alive 이벤트는 id 가 null 일 수 있으므로 명시적 분기.
                SseEmitter.SseEventBuilder builder = SseEmitter.event()
                    .name(event.type().name())
                    .data(event, MediaType.APPLICATION_JSON);
                if (event.id() != null) {
                    builder = builder.id(event.id());
                }
                emitter.send(builder);
            } catch (IOException | IllegalStateException ex) {
                remove(emitter);
            }
        }
    }

    private <K> void removeFrom(Map<K, CopyOnWriteArrayList<SseEmitter>> registry, SseEmitter emitter) {
        registry.forEach((key, emitters) -> {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                registry.remove(key, emitters);
            }
        });
    }
}
