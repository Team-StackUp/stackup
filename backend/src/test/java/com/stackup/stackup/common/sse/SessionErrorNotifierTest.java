package com.stackup.stackup.common.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SessionErrorNotifierTest {

    @Mock ApplicationEventPublisher events;
    @InjectMocks SessionErrorNotifier notifier;

    @Test
    void notify_publishesErrorToSessionAndUserChannels() {
        SessionErrorNotice notice = new SessionErrorNotice(
            50L, "FEEDBACK", "FEEDBACK_GENERATION_FAILED", "피드백 생성에 실패했습니다.", true);

        notifier.notify(50L, 1L, notice);

        ArgumentCaptor<Object> cap = ArgumentCaptor.forClass(Object.class);
        verify(events, times(2)).publishEvent(cap.capture());
        assertThat(cap.getAllValues())
            .allSatisfy(e -> {
                RealtimeNotifyEvent rne = (RealtimeNotifyEvent) e;
                assertThat(rne.type()).isEqualTo(SseEventType.ERROR);
                assertThat(rne.payload()).isSameAs(notice);
            });
        assertThat(cap.getAllValues())
            .extracting(e -> ((RealtimeNotifyEvent) e).channel())
            .containsExactlyInAnyOrder(
                RealtimeNotifyEvent.Channel.SESSION, RealtimeNotifyEvent.Channel.USER);
        assertThat(cap.getAllValues())
            .extracting(e -> ((RealtimeNotifyEvent) e).id())
            .containsExactlyInAnyOrder(50L, 1L);
    }
}
