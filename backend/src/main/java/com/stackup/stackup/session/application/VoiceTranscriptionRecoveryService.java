package com.stackup.stackup.session.application;

import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.MessageStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// STT 콜백 유실 복구. 스위퍼가 메시지마다 이 서비스를 호출한다(각자 독립 트랜잭션).
@Service
@RequiredArgsConstructor
public class VoiceTranscriptionRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(VoiceTranscriptionRecoveryService.class);
    private static final String ERROR_CODE = "STT_CALLBACK_TIMEOUT";

    private final InterviewMessageRepository messageRepository;
    private final ApplicationEventPublisher events;

    @Transactional
    public void failStaleTranscription(Long messageId) {
        InterviewMessage message = messageRepository.findById(messageId).orElse(null);
        if (message == null) {
            return;
        }
        // 스위퍼가 목록을 만든 뒤 콜백이 도착했을 수 있다 — 이미 채워졌으면 건드리지 않는다.
        if (message.getStatus() != MessageStatus.CREATED
            || !InterviewMessage.VOICE_TRANSCRIPTION_PENDING_TEXT.equals(message.getContent())) {
            return;
        }
        message.failVoiceTranscription();

        Long sessionId = message.getSession().getId();
        VoiceCallbackService.VoiceFailedNotice notice = new VoiceCallbackService.VoiceFailedNotice(
            sessionId, message.getId(), ERROR_CODE, message.getContent());
        events.publishEvent(RealtimeNotifyEvent.session(sessionId, SseEventType.SESSION_MESSAGE, notice));
        events.publishEvent(RealtimeNotifyEvent.user(message.getSession().getUser().getId(),
            SseEventType.SESSION_MESSAGE, notice));
        log.warn("voice answer stuck in transcription — marked FAILED so the turn unlocks. "
            + "sessionId={}, messageId={}", sessionId, messageId);
    }
}
