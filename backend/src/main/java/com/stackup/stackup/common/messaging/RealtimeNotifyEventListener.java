package com.stackup.stackup.common.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 도메인 트랜잭션 커밋 후 RealTime 알림을 발행한다. 롤백 시 미발행 → DB 와 알림의 일관성 보장.
@Component
@RequiredArgsConstructor
public class RealtimeNotifyEventListener {

    private final RealtimeNotifyPublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(RealtimeNotifyEvent event) {
        switch (event.channel()) {
            case SESSION -> publisher.publishToSession(event.id(), event.type(), event.payload());
            case USER -> publisher.publishToUser(event.id(), event.type(), event.payload());
            case DOCUMENT -> publisher.publishToDocument(event.id(), event.type(), event.payload());
        }
    }
}
