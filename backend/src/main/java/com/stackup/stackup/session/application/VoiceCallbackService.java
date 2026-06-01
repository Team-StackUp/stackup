package com.stackup.stackup.session.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackup.stackup.common.messaging.domain.ProcessedMessage;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.dto.VoiceCallbackEnvelope;
import com.stackup.stackup.session.application.dto.VoiceCallbackPayload;
import com.stackup.stackup.session.application.event.AnswerSubmittedEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.MessageVoiceAnalysis;
import com.stackup.stackup.session.domain.MessageVoiceAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// callback.voice 처리 — InterviewMessage.content 채움 + MessageVoiceAnalysis INSERT + AnswerSubmittedEvent 발화.
// AnswerSubmittedEvent → SessionFollowupRequester 가 generate.followup 발행 (텍스트 답변과 동일 흐름).
// 멱등: processed_messages + message_voice_analyses UNIQUE(message_id).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoiceCallbackService {

    private static final Logger log = LoggerFactory.getLogger(VoiceCallbackService.class);
    private static final String CONSUMER_NAME = "core.callback.voice";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final InterviewMessageRepository messageRepository;
    private final MessageVoiceAnalysisRepository voiceAnalysisRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ApplicationEventPublisher events;

    @Transactional
    public void apply(VoiceCallbackEnvelope envelope) {
        if (envelope == null || envelope.payload() == null) {
            log.warn("callback.voice envelope or payload is null — skip");
            return;
        }
        if (isProcessed(envelope.messageId())) {
            log.info("callback.voice duplicate, skip. messageId={}", envelope.messageId());
            return;
        }
        VoiceCallbackPayload p = envelope.payload();
        if (p.interviewMessageId() == null) {
            log.warn("callback.voice missing interviewMessageId. messageId={}", envelope.messageId());
            markProcessed(envelope.messageId());
            return;
        }
        InterviewMessage message = messageRepository.findById(p.interviewMessageId()).orElse(null);
        if (message == null) {
            log.warn("callback.voice message not found. id={}", p.interviewMessageId());
            markProcessed(envelope.messageId());
            return;
        }

        if (p.errorCode() != null && !p.errorCode().isBlank()) {
            failVoiceMessage(p.sessionId(), message, p.errorCode());
            markProcessed(envelope.messageId());
            log.warn("callback.voice STT failed. sessionId={}, msg={}, code={}",
                p.sessionId(), message.getId(), p.errorCode());
            return;
        }
        if (p.transcript() == null || p.transcript().isBlank()) {
            failVoiceMessage(p.sessionId(), message, "STT_EMPTY_TRANSCRIPT");
            markProcessed(envelope.messageId());
            log.warn("callback.voice STT empty transcript. sessionId={}, msg={}",
                p.sessionId(), message.getId());
            return;
        }

        message.completeWithTranscript(p.transcript());

        if (!voiceAnalysisRepository.existsByMessage_Id(message.getId())) {
            try {
                voiceAnalysisRepository.save(MessageVoiceAnalysis.of(
                    message,
                    p.speakingRateWpm(),
                    p.silenceDurationSec(),
                    fillerToJson(p.fillerWordCounts()),
                    p.pronunciationAccuracy()
                ));
            } catch (DataIntegrityViolationException ignored) {
                // race
            }
        }

        events.publishEvent(RealtimeNotifyEvent.session(p.sessionId(), SseEventType.SESSION_MESSAGE,
            new VoiceTranscribedNotice(p.sessionId(), message.getId(), p.transcript())));
        events.publishEvent(RealtimeNotifyEvent.user(message.getSession().getUser().getId(),
            SseEventType.SESSION_MESSAGE,
            new VoiceTranscribedNotice(p.sessionId(), message.getId(), p.transcript())));

        // 텍스트 답변과 동일 흐름으로 followup 트리거.
        Long parentId = message.getParentMessage() == null ? null : message.getParentMessage().getId();
        events.publishEvent(new AnswerSubmittedEvent(
            message.getSession().getUser().getId(),
            p.sessionId(),
            parentId,
            message.getId()
        ));

        markProcessed(envelope.messageId());
        log.info("callback.voice processed. sessionId={}, msg={}, wpm={}, filler={}",
            p.sessionId(), message.getId(), p.speakingRateWpm(),
            p.fillerWordCounts() == null ? 0 : p.fillerWordCounts().size());
    }

    public record VoiceTranscribedNotice(Long sessionId, Long messageId, String transcript) {
    }

    public record VoiceFailedNotice(Long sessionId, Long messageId, String errorCode, String fallbackText) {
    }

    private void failVoiceMessage(Long sessionId, InterviewMessage message, String errorCode) {
        message.failVoiceTranscription();
        VoiceFailedNotice notice = new VoiceFailedNotice(
            sessionId,
            message.getId(),
            errorCode,
            message.getContent()
        );
        events.publishEvent(RealtimeNotifyEvent.session(sessionId, SseEventType.SESSION_MESSAGE, notice));
        events.publishEvent(RealtimeNotifyEvent.user(message.getSession().getUser().getId(),
            SseEventType.SESSION_MESSAGE, notice));
    }

    private String fillerToJson(java.util.Map<String, Integer> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("filler_word_counts json serialize failed", e);
            return null;
        }
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
}
