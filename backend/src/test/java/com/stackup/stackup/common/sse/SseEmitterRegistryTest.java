package com.stackup.stackup.common.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.common.config.properties.SseProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRegistryTest {

    @Test
    void keepAliveAll_does_not_throw_when_event_id_is_null() {
        SseEmitterRegistry registry = newRegistry();
        SseEmitter emitter = registry.registerUser(1L);
        assertThat(emitter).isNotNull();

        // id=null 인 keep-alive 가 NPE 없이 전파되어야 함 (Spring SseEventBuilder.id(null) 회귀 방지)
        registry.keepAliveAll();
    }

    @Test
    void sendToUser_with_null_id_event_succeeds() {
        SseEmitterRegistry registry = newRegistry();
        registry.registerUser(42L);

        SseEvent event = new SseEvent(null, SseEventType.DOC_STATE, "payload",
            java.time.Instant.now(), null);

        // 도메인 코드에서 id 를 채우지 않은 케이스에도 안전해야 함
        registry.sendToUser(42L, event);
    }

    private static SseEmitterRegistry newRegistry() {
        SseProperties properties = new SseProperties(
            Duration.ofSeconds(30),
            Duration.ofSeconds(10),
            Duration.ofSeconds(60)
        );
        return new SseEmitterRegistry(properties);
    }
}
