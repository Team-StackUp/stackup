package com.stackup.stackup.common.messaging;

import com.stackup.stackup.common.sse.SseEventType;

// Core 도메인 트랜잭션 안에서 발행하는 알림 의도. 실제 RabbitMQ publish 는
// RealtimeNotifyEventListener 가 AFTER_COMMIT 에서 수행한다 (롤백 시 미발행 보장).
public record RealtimeNotifyEvent(Channel channel, Long id, SseEventType type, Object payload) {
    public enum Channel { SESSION, USER, DOCUMENT }

    public static RealtimeNotifyEvent session(Long sessionId, SseEventType type, Object payload) {
        return new RealtimeNotifyEvent(Channel.SESSION, sessionId, type, payload);
    }

    public static RealtimeNotifyEvent user(Long userId, SseEventType type, Object payload) {
        return new RealtimeNotifyEvent(Channel.USER, userId, type, payload);
    }

    public static RealtimeNotifyEvent document(Long documentId, SseEventType type, Object payload) {
        return new RealtimeNotifyEvent(Channel.DOCUMENT, documentId, type, payload);
    }
}
