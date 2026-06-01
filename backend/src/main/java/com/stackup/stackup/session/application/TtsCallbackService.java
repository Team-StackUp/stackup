package com.stackup.stackup.session.application;

import com.stackup.stackup.common.messaging.domain.ProcessedMessage;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.dto.TtsCallbackEnvelope;
import com.stackup.stackup.session.application.dto.TtsCallbackPayload;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.TtsStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TtsCallbackService {

    private static final Logger log = LoggerFactory.getLogger(TtsCallbackService.class);
    private static final String CONSUMER_NAME = "core.callback.tts";

    private final InterviewMessageRepository messageRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ApplicationEventPublisher events;

    @Transactional
    public void apply(TtsCallbackEnvelope envelope) {
        if (envelope == null || envelope.payload() == null) {
            log.warn("callback.tts envelope or payload is null - skip");
            return;
        }
        if (isProcessed(envelope.messageId())) {
            log.info("callback.tts duplicate, skip. messageId={}", envelope.messageId());
            return;
        }

        TtsCallbackPayload p = envelope.payload();
        if (p.messageId() == null || p.sessionId() == null) {
            log.warn("callback.tts missing sessionId or messageId. messageId={}", envelope.messageId());
            markProcessed(envelope.messageId());
            return;
        }

        InterviewMessage message = messageRepository.findById(p.messageId()).orElse(null);
        if (message == null) {
            log.warn("callback.tts message not found. id={}", p.messageId());
            markProcessed(envelope.messageId());
            return;
        }
        if (!p.sessionId().equals(message.getSession().getId()) || message.getRole() != MessageRole.INTERVIEWER) {
            log.warn("callback.tts ignored non-question or mismatched message. sessionId={}, msg={}",
                p.sessionId(), p.messageId());
            markProcessed(envelope.messageId());
            return;
        }

        if (p.isSucceeded()) {
            if (p.audioKey() == null || p.audioKey().isBlank()) {
                message.failTts();
                publishNotice(message, "TTS_EMPTY_AUDIO");
                log.warn("callback.tts success without audio key. sessionId={}, msg={}", p.sessionId(), p.messageId());
            } else {
                message.completeTts(p.audioKey(), p.durationSec());
                publishNotice(message, null);
                log.info("callback.tts processed. sessionId={}, msg={}, durationSec={}",
                    p.sessionId(), p.messageId(), p.durationSec());
            }
        } else if (p.isFailed()) {
            message.failTts();
            publishNotice(message, p.errorCode());
            log.warn("callback.tts failed. sessionId={}, msg={}, code={}",
                p.sessionId(), p.messageId(), p.errorCode());
        } else {
            log.warn("callback.tts unknown status={}. sessionId={}, msg={}",
                p.status(), p.sessionId(), p.messageId());
        }

        markProcessed(envelope.messageId());
    }

    private void publishNotice(InterviewMessage message, String errorCode) {
        Long sessionId = message.getSession().getId();
        TtsUpdatedNotice notice = new TtsUpdatedNotice(
            sessionId,
            message.getId(),
            message.getTtsStatus(),
            message.getTtsAudioPath(),
            message.getTtsDurationSec(),
            errorCode
        );
        events.publishEvent(RealtimeNotifyEvent.session(sessionId, SseEventType.SESSION_MESSAGE, notice));
        events.publishEvent(RealtimeNotifyEvent.user(message.getSession().getUser().getId(),
            SseEventType.SESSION_MESSAGE, notice));
    }

    private boolean isProcessed(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        return processedMessageRepository.existsById(messageId);
    }

    private void markProcessed(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        try {
            processedMessageRepository.save(ProcessedMessage.of(messageId, CONSUMER_NAME));
        } catch (DataIntegrityViolationException ignored) {
            // race
        }
    }

    public record TtsUpdatedNotice(
        Long sessionId,
        Long messageId,
        TtsStatus status,
        String audioPath,
        Double durationSec,
        String errorCode
    ) {
    }
}
