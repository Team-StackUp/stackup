package com.stackup.stackup.common.sse;

import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

// 세션 도메인 실패의 SSE ERROR 발행 단일 구현 — 세션 채널(라이브 화면)과 유저 채널(워크스페이스)
// 양쪽에 같은 notice 를 쏜다. Questions/Feedback 콜백 서비스가 각자 인라인으로 들고 있던
// 이중 발행 복사본을 이곳으로 모았다. common → 도메인 역참조가 없도록 id 만 받는다.
@Component
@RequiredArgsConstructor
public class SessionErrorNotifier {

    private final ApplicationEventPublisher events;

    public void notify(Long sessionId, Long userId, SessionErrorNotice notice) {
        events.publishEvent(RealtimeNotifyEvent.session(sessionId, SseEventType.ERROR, notice));
        events.publishEvent(RealtimeNotifyEvent.user(userId, SseEventType.ERROR, notice));
    }
}
