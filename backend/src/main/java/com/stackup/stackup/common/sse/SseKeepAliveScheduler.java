package com.stackup.stackup.common.sse;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SseKeepAliveScheduler {

    private final SseEmitterRegistry registry;

    public SseKeepAliveScheduler(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    @Scheduled(fixedDelayString = "${app.sse.keep-alive-interval}")
    public void sendKeepAlive() {
        registry.keepAliveAll();
    }
}
